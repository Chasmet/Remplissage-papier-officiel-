package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReleaseNotesTest { @Test public void mentionsLocalCorrection(){assertTrue(PrecisionReleaseNotes.text().contains("correction locale"));} }
