package com.chasmet.remplissagepapierofficiel;
public final class CorrectionPreviewPolicy { private CorrectionPreviewPolicy(){} public static boolean shouldRenderAfter(String operation){return CorrectionOperationNames.UPDATE.equals(operation)||CorrectionOperationNames.DELETE.equals(operation)||"add_overlay".equals(operation);} }
