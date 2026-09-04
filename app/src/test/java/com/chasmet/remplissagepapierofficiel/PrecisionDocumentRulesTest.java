package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionDocumentRulesTest { @Test public void updatePreservesData() throws Exception {assertTrue(PrecisionDocumentRules.json().getBoolean("preserve_document_data_on_app_update"));} }
