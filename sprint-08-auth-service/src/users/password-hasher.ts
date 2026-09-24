import { Injectable } from "@nestjs/common";
import * as argon2 from "argon2";

/**
 * argon2id, following OWASP guidance for password storage. Uses the
 * m=64 MiB, t=3 profile with parallelism reduced to 1 (profile suggests 4)
 * to limit CPU per login request and the DoS exposure it creates.
 * Measured at roughly 110ms per hash/verify locally.
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
 * Dummy argon2id hash, computed once. Unknown usernames are verified
 * against it so login timing matches the wrong-password case.
 */
export function getDummyPasswordHash(): Promise<string> {
  if (!dummyHashPromise) {
    dummyHashPromise = argon2.hash("dummy-password-for-timing-parity-only", ARGON2_OPTIONS);
  }
  return dummyHashPromise;
}
