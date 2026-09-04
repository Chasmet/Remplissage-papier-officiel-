package com.chasmet.remplissagepapierofficiel;
import org.junit.Test;import static org.junit.Assert.*;
public class PrecisionMcpTodoTest { @Test public void updateListed(){assertTrue(PrecisionMcpTodo.json().toString().contains("paper_update_overlay"));} }
