package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionPreviewReviewTest { @Test public void fullPreviewReady() throws Exception {assertTrue(PrecisionPreviewReview.json(2,true,true).getBoolean("ready_for_visual_review"));} }
