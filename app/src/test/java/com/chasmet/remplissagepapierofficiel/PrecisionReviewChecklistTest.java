package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionReviewChecklistTest { @Test public void baselineReviewed(){assertTrue(PrecisionReviewChecklist.json().toString().contains("baseline"));} }
