package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import java.util.LinkedHashSet;
import static org.junit.Assert.*;
public class CorrectionPassTest { @Test public void requestsCropForChangedOverlay() throws Exception { LinkedHashSet<String>s=new LinkedHashSet<>();s.add("email_01");assertTrue(CorrectionPass.json(2,s).getBoolean("crops_required")); } }
