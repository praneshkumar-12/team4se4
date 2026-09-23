module.exports = {
  preset: "ts-jest",
  testEnvironment: "node",
  rootDir: "src",
  testRegex: ".*\\.spec\\.ts$",
  collectCoverageFrom: ["**/*.(t|j)s"],
  coverageDirectory: "../coverage",
};
