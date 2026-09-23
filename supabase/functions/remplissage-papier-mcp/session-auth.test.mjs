import assert from "node:assert/strict";
import { sessionTokenFromRequest } from "./session-auth.ts";

const token = "a".repeat(43);
const other = "b".repeat(43);
const endpoint = "https://example.invalid/functions/v1/remplissage-papier-mcp";
const request = (suffix = "", headers = {}) => new Request(endpoint + suffix, { headers });

assert.equal(sessionTokenFromRequest(request()), "");
assert.equal(sessionTokenFromRequest(request("", { Authorization: `Bearer ${token}` })), token);
assert.equal(sessionTokenFromRequest(request("", { Authorization: `bearer ${token}` })), token);
assert.equal(sessionTokenFromRequest(request(`?session=${token}`)), token);
assert.equal(sessionTokenFromRequest(request("", { "x-remplissage-session": token })), token);
assert.equal(sessionTokenFromRequest(request(`?session=${token}`, { Authorization: `Bearer ${token}` })), token);
assert.equal(sessionTokenFromRequest(request(`?session=${token}`, { Authorization: `Bearer ${other}` })), "");
assert.equal(sessionTokenFromRequest(request("", { Authorization: "Basic abc" })), "");
assert.equal(sessionTokenFromRequest(request("", { Authorization: "Bearer short" })), "");

console.log("session auth tests: ok");
