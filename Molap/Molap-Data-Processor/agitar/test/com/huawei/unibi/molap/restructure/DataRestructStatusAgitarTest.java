/**
 * Generated by Agitar build: AgitarOne Version 5.3.0.000022 (Build date: Jan 04, 2012) [5.3.0.000022]
 * JDK Version: 1.6.0_14
 *
 * Generated on Jul 31, 2013 6:18:46 PM
 * Time to generate: 00:09.690 seconds
 *
 */

package com.huawei.unibi.molap.restructure;

import com.agitar.lib.junit.AgitarTestCase;

public class DataRestructStatusAgitarTest extends AgitarTestCase {
    
    public Class getTargetClass()  {
        return DataRestructStatus.class;
    }
    
    public void testGetType() throws Throwable {
        String result = DataRestructStatus.INPROGRESS.getType();
        assertEquals("result", "InProgress", result);
    }
    
    public void testValueOf() throws Throwable {
        DataRestructStatus result = DataRestructStatus.valueOf("SUCCESS");
        assertEquals("result", DataRestructStatus.SUCCESS, result);
    }
    
    public void testValues() throws Throwable {
        DataRestructStatus[] result = DataRestructStatus.values();
        assertEquals("result.length", 4, result.length);
        assertEquals("result[0]", DataRestructStatus.INPROGRESS, result[0]);
    }
    
    public void testValueOfThrowsIllegalArgumentException() throws Throwable {
        try {
            DataRestructStatus.valueOf("testDataRestructStatusParam1");
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException ex) {
            assertEquals("ex.getMessage()", "No enum const class com.huawei.unibi.molap.restructure.DataRestructStatus.testDataRestructStatusParam1", ex.getMessage());
            assertThrownBy(Enum.class, ex);
        }
    }
    
    public void testValueOfThrowsNullPointerException() throws Throwable {
        try {
            DataRestructStatus.valueOf(null);
            fail("Expected NullPointerException to be thrown");
        } catch (NullPointerException ex) {
            assertEquals("ex.getMessage()", "Name is null", ex.getMessage());
            assertThrownBy(Enum.class, ex);
        }
    }
}
