package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionDoneDefinitionTest { @Test public void mcpAndCiRequired(){assertTrue(PrecisionDoneDefinition.text().contains("MCP"));assertTrue(PrecisionDoneDefinition.text().contains("CI"));} }
