import { UnauthorizedException } from "@nestjs/common";
import { createHash } from "crypto";
import { RefreshTokenRepository, RefreshTokenRow } from "./refresh-token.repository";
import { RefreshTokenService } from "./refresh-token.service";

const USER_ID = "8f14e45f-ceea-4c1b-9d3b-1a2b3c4d5e6f";

function hash(token: string): string {
  return createHash("sha256").update(token).digest("hex");
}

/** In-memory stand-in for the Postgres-backed repository - no database. */
class FakeRefreshTokenRepository {
  rows = new Map<string, RefreshTokenRow & { tokenHash: string }>();

  async create(userId: string, tokenHash: string, expiresAt: Date): Promise<void> {
    const id = `row-${this.rows.size + 1}`;
    this.rows.set(tokenHash, { id, userId, revokedAt: null, expiresAt, tokenHash });
  }

  async findByHash(tokenHash: string): Promise<RefreshTokenRow | null> {
    return this.rows.get(tokenHash) ?? null;
  }

  async revoke(id: string): Promise<void> {
    for (const row of this.rows.values()) {
      if (row.id === id && row.revokedAt === null) {
        row.revokedAt = new Date();
      }
    }
  }

  async revokeAllForUser(userId: string): Promise<void> {
    for (const row of this.rows.values()) {
      if (row.userId === userId && row.revokedAt === null) {
        row.revokedAt = new Date();
      }
    }
  }
}

function buildService(): { service: RefreshTokenService; repo: FakeRefreshTokenRepository } {
  const repo = new FakeRefreshTokenRepository();
  return { service: new RefreshTokenService(repo as unknown as RefreshTokenRepository), repo };
}

describe("RefreshTokenService", () => {
  it("stores only a hash of the issued token, never the token itself", async () => {
    const { service, repo } = buildService();
    const token = await service.issue(USER_ID);

    const stored = [...repo.rows.keys()];
    expect(stored).not.toContain(token);
    expect(stored).toContain(hash(token));
  });

  it("refresh returns a new refresh token, different from the one presented", async () => {
    const { service } = buildService();
    const original = await service.issue(USER_ID);

    const { refreshToken, userId } = await service.rotate(original);

    expect(userId).toBe(USER_ID);
    expect(refreshToken).not.toBe(original);
  });

  it("the newly issued refresh token works", async () => {
    const { service } = buildService();
    const original = await service.issue(USER_ID);
    const { refreshToken: rotated } = await service.rotate(original);

    await expect(service.rotate(rotated)).resolves.toMatchObject({ userId: USER_ID });
  });

  it("a token that has already been exchanged is refused, and revokes every live token for that user", async () => {
    const { service } = buildService();
    const original = await service.issue(USER_ID);
    const another = await service.issue(USER_ID);
    await service.rotate(original); // consumes `original`

    // Presenting `original` a second time is the declared revocation
    // behaviour: AUTH-401, and it takes `another` down with it.
    await expect(service.rotate(original)).rejects.toThrow(UnauthorizedException);
    await expect(service.rotate(another)).rejects.toThrow(UnauthorizedException);
  });

  it("refuses a refresh token that was never issued", async () => {
    const { service } = buildService();
    await expect(service.rotate("not-a-real-token")).rejects.toThrow(UnauthorizedException);
  });
});
