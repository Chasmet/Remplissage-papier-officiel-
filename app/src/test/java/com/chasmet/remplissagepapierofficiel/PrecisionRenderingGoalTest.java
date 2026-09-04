package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionRenderingGoalTest { @Test public void goalAvoidsOverlap(){assertTrue(PrecisionRenderingGoal.value().contains("aucun chevauchement"));} }
