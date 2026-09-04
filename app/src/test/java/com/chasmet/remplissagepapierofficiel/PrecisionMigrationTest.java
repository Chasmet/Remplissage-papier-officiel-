package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PrecisionMigrationTest { @Test public void coordinatesUnchanged(){TextOverlay a=new TextOverlay(0,.605f,.237f,"x",8f);TextOverlay b=PrecisionMigration.legacy(a);assertEquals(a.x,b.x,0);assertEquals(a.y,b.y,0);} }
