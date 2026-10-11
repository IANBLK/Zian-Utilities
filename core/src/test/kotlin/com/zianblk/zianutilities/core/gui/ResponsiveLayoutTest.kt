package com.zianblk.zianutilities.core.gui

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ResponsiveLayoutTest {
    @Test fun `corners fit and clicks roundtrip at GUI scales and small windows`() {
        for ((w,h) in listOf(960 to 540,640 to 360,480 to 270,320 to 240,200 to 150)) {
            val layout=ResponsiveLayout.fit(w,h,440,330)
            assertTrue(layout.scale()>0 && layout.scale()<=1)
            assertTrue(layout.x()>=-1e-8 && layout.y()>=-1e-8)
            assertTrue(layout.x()+layout.width()*layout.scale()<=w+1e-8)
            assertTrue(layout.y()+layout.height()*layout.scale()<=h+1e-8)
            for ((x,y) in listOf(0.0 to 0.0,100.25 to 75.75,layout.width().toDouble() to layout.height().toDouble())) {
                assertEquals(x,layout.mouseX(layout.x()+x*layout.scale()),1e-8)
                assertEquals(y,layout.mouseY(layout.y()+y*layout.scale()),1e-8)
            }
        }
    }
    @Test fun `large approved layout is not enlarged`() {
        val layout=ResponsiveLayout.fit(960,540,440,330)
        assertEquals(1.0,layout.scale());assertEquals(960,layout.width());assertEquals(540,layout.height())
    }
    @Test fun `invalid dimensions are refused`() {
        assertThrows(IllegalArgumentException::class.java){ResponsiveLayout.fit(0,270,440,330)}
    }
}
