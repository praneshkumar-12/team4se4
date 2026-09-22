import { Injectable } from "@nestjs/common";
import * as argon2 from "argon2";

/**
 * argon2id over bcrypt: OWASP's current first recommendation for password
 * storage, and resistant to the cheap GPU/ASIC parallelism that makes a
 * general-purpose or even a bcrypt hash comparatively weaker at the same
 * wall-clock cost. Parameters are OWASP's "second choice" argon2id profile
 * (m=64 MiB, t=3), with parallelism pinned to 1 rather than the profile's
 * p=4: this route runs on every login request, and a higher parallelism
 * multiplies the CPU a single request can consume, which is the DoS the
 * ticket warns about ("too high and your login route is the cheapest
 * denial-of-service target in the platform"). Measured locally: ~110ms per
 * hash/verify, in the "tenth of a second" range the ticket asks for.
 */
const ARGON2_OPTIONS: argon2.Options & { raw?: false } = {
  type: argon2.argon2id,
  memoryCost: 65536, // 64 MiB
  timeCost: 3,
  parallelism: 1,
};

@Injectable()
export class PasswordHasher {
  hash(password: string): Promise<string> {
    return argon2.hash(password, ARGON2_OPTIONS);
  }

  verify(hash: string, password: string): Promise<boolean> {
    return argon2.verify(hash, password);
  }
}

let dummyHashPromise: Promise<string> | null = null;

/**
 * A fixed argon2id hash of a well-known dummy password, computed once and
 * reused. SEC4-628's uniform-failure path verifies an unknown username's
 * supplied password against this instead of returning early, so an unknown
 * username costs the same wall-clock time as a wrong password.
 */
export function getDummyPasswordHash(): Promise<string> {
  if (!dummyHashPromise) {
    dummyHashPromise = argon2.hash("dummy-password-for-timing-parity-only", ARGON2_OPTIONS);
  }
  return dummyHashPromise;
}
