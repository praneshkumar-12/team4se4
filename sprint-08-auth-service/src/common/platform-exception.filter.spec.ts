import { ArgumentsHost, ConflictException, UnauthorizedException, UnprocessableEntityException } from "@nestjs/common";
import { PlatformExceptionFilter } from "./platform-exception.filter";

function mockHost() {
  const status = jest.fn().mockReturnThis();
  const json = jest.fn();
  const response = { status, json };
  const host = { switchToHttp: () => ({ getResponse: () => response }) } as unknown as ArgumentsHost;
  return { host, status, json };
}

describe("PlatformExceptionFilter", () => {
  const filter = new PlatformExceptionFilter();

  it("maps a 401 to the AUTH-401 envelope, nothing else", () => {
    const { host, status, json } = mockHost();
    filter.catch(new UnauthorizedException(), host);
    expect(status).toHaveBeenCalledWith(401);
    expect(json).toHaveBeenCalledWith({ errorCode: "AUTH-401", message: "Unauthorised" });
  });

  it("maps a 409 to the AUTH-409 envelope", () => {
    const { host, status, json } = mockHost();
    filter.catch(new ConflictException(), host);
    expect(status).toHaveBeenCalledWith(409);
    expect(json).toHaveBeenCalledWith({ errorCode: "AUTH-409", message: "Registration failed" });
  });

  it("maps a 422 to the VAL-422 envelope", () => {
    const { host, status, json } = mockHost();
    filter.catch(new UnprocessableEntityException(), host);
    expect(status).toHaveBeenCalledWith(422);
    expect(json).toHaveBeenCalledWith({ errorCode: "VAL-422", message: "Invalid input" });
  });

  it("carries no field beyond errorCode and message", () => {
    const { host, json } = mockHost();
    filter.catch(new UnauthorizedException(), host);
    expect(Object.keys(json.mock.calls[0][0]).sort()).toEqual(["errorCode", "message"]);
  });
});
