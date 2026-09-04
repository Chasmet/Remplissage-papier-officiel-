package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionProtocolDocumentTest { @Test public void hasDefinitionOfDone() throws Exception {assertTrue(PrecisionProtocolDocument.json().getString("definition_of_done").contains("MCP"));} }
