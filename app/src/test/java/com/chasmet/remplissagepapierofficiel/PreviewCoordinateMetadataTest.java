package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreviewCoordinateMetadataTest { @Test public void pixelScaleExact() throws Exception {assertEquals(.000625,PreviewCoordinateMetadata.json(1000,1600).getDouble("normalized_y_per_pixel"),.0000001);} }
