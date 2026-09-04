package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReleaseChecklistTest { @Test public void mcpRequired(){assertTrue(PrecisionReleaseChecklist.json().toString().contains("MCP 4.1"));} }
