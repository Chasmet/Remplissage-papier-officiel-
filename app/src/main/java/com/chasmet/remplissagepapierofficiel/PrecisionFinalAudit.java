package com.chasmet.remplissagepapierofficiel;
import org.json.JSONObject;
public final class PrecisionFinalAudit { private PrecisionFinalAudit(){} public static JSONObject json() throws Exception {return new JSONObject().put("protocol_document",PrecisionProtocolDocument.json()).put("implementation",PrecisionImplementationStatus.json()).put("ci_expectation",PrecisionCiExpectation.json()).put("mcp_todo",PrecisionMcpTodo.json()).put("branch",PrecisionBranchInfo.BRANCH);} }
