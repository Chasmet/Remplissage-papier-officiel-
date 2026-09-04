package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionMcpAliasesTest { @Test public void pageImageAlias() throws Exception {assertEquals("paper_get_page_image",PrecisionMcpAliases.json().getString("get_page_image"));} }
