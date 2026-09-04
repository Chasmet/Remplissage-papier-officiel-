package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class CoordinateTransformTest {
 @Test public void roundTripIsExactEnough(){ float n=.605f; float px=CoordinateTransform.x(n,1600f); assertEquals(n,CoordinateTransform.normalizeX(px,1600f),.000001f); }
}
