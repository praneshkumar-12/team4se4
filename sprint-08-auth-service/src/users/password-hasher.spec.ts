import { PasswordHasher, getDummyPasswordHash } from "./password-hasher";

describe("PasswordHasher", () => {
  const hasher = new PasswordHasher();

  it("a correct password verifies", async () => {
    const hash = await hasher.hash("correct horse battery staple");
    await expect(hasher.verify(hash, "correct horse battery staple")).resolves.toBe(true);
  });

  it("an incorrect password fails verification", async () => {
    const hash = await hasher.hash("correct horse battery staple");
    await expect(hasher.verify(hash, "wrong password entirely")).resolves.toBe(false);
  });

  it("does not use a general-purpose digest such as MD5 or SHA", async () => {
    const hash = await hasher.hash("correct horse battery staple");

    // A bare MD5/SHA digest is fixed-length hex with no algorithm tag or
    // cost parameters. argon2id's encoded output names its own algorithm,
    // version and cost factors, which is the property a general-purpose
    // digest does not have.
    expect(hash.startsWith("$argon2id$")).toBe(true);
    expect(hash).not.toMatch(/^[0-9a-f]{32}$/); // not a bare MD5 hex digest
    expect(hash).not.toMatch(/^[0-9a-f]{64}$/); // not a bare SHA-256 hex digest
  });

  it("computes a stable dummy hash for the uniform-failure path (SEC4-628)", async () => {
    const first = await getDummyPasswordHash();
    const second = await getDummyPasswordHash();
    expect(first).toBe(second); // computed once, reused
    expect(first.startsWith("$argon2id$")).toBe(true);
  });
});
