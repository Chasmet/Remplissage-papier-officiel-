package com.chasmet.remplissagepapierofficiel;

import org.junit.Test;
import static org.junit.Assert.*;
public class UnknownValuePolicyTest {
 @Test public void unknownCannotRenderInventedText(){ TextOverlay o=new TextOverlay("u",0,.2f,.2f,"inventé",8f,"left","text",0,0,"unknown"); assertFalse(UnknownValuePolicy.mayRenderText(o)); }
}
