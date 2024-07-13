// @formatter:off
@file:Suppress("RedundantVisibilityModifier")

// Generated by me.him188.ani.utils.bbcode.BBCodeTestGenerator
package me.him188.ani.utils.bbcode

import kotlin.test.Test

public class GenBBSpacesTest : BBCodeParserTestHelper() {
    @Test
    public fun parse137138206() {
        BBCode.parse("[b]Hello World![/b]")
        .run {
            assertText(elements.at(0), value="Hello World!", bold=true)
        }
    }

    @Test
    public fun parse2052759877() {
        BBCode.parse("[b][/b]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse842178951() {
        BBCode.parse("[b] [/b]")
        .run {
            assertText(elements.at(0), value=" ", bold=true)
        }
    }

    @Test
    public fun parse1488478815() {
        BBCode.parse("[b] /[][/]Hello [/b]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", bold=true)
        }
    }

    @Test
    public fun parse1473290132() {
        BBCode.parse("[i]Hello World![/i]")
        .run {
            assertText(elements.at(0), value="Hello World!", italic=true)
        }
    }

    @Test
    public fun parse2041803145() {
        BBCode.parse("[i][/i]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse1075379737() {
        BBCode.parse("[i] [/i]")
        .run {
            assertText(elements.at(0), value=" ", italic=true)
        }
    }

    @Test
    public fun parse127856769() {
        BBCode.parse("[i] /[][/]Hello [/i]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", italic=true)
        }
    }

    @Test
    public fun parse1901643140() {
        BBCode.parse("[u]Hello World![/u]")
        .run {
            assertText(elements.at(0), value="Hello World!", underline=true)
        }
    }

    @Test
    public fun parse1698252961() {
        BBCode.parse("[u][/u]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse1159477607() {
        BBCode.parse("[u] [/u]")
        .run {
            assertText(elements.at(0), value=" ", underline=true)
        }
    }

    @Test
    public fun parse1671584257() {
        BBCode.parse("[u] /[][/]Hello [/u]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", underline=true)
        }
    }

    @Test
    public fun parse92501504() {
        BBCode.parse("[s]Hello World![/s]")
        .run {
            assertText(elements.at(0), value="Hello World!", strikethrough=true)
        }
    }

    @Test
    public fun parse1755511325() {
        BBCode.parse("[s][/s]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse1360482265() {
        BBCode.parse("[s] [/s]")
        .run {
            assertText(elements.at(0), value=" ", strikethrough=true)
        }
    }

    @Test
    public fun parse17359423() {
        BBCode.parse("[s] /[][/]Hello [/s]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", strikethrough=true)
        }
    }

    @Test
    public fun parse1502622152() {
        BBCode.parse("[url]Hello World![/url]")
        .run {
            assertText(elements.at(0), value="Hello World!", jumpUrl="Hello World!")
        }
    }

    @Test
    public fun parse1108237035() {
        BBCode.parse("[url][/url]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse367631961() {
        BBCode.parse("[url] [/url]")
        .run {
            assertText(elements.at(0), value=" ", jumpUrl=" ")
        }
    }

    @Test
    public fun parse493385023() {
        BBCode.parse("[url] /[][/]Hello [/url]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", jumpUrl=" /[][/]Hello ")
        }
    }

    @Test
    public fun parse1176497952() {
        BBCode.parse("[img]Hello World![/img]")
        .run {
            assertImage(elements.at(0), imageUrl="Hello World!")
        }
    }

    @Test
    public fun parse505710013() {
        BBCode.parse("[img][/img]")
        .run {
            assertImage(elements.at(0), imageUrl="")
        }
    }

    @Test
    public fun parse1885754585() {
        BBCode.parse("[img] [/img]")
        .run {
            assertImage(elements.at(0), imageUrl=" ")
        }
    }

    @Test
    public fun parse1015657025() {
        BBCode.parse("[img] /[][/]Hello [/img]")
        .run {
            assertImage(elements.at(0), imageUrl=" /[][/]Hello ")
        }
    }

    @Test
    public fun parse872695250() {
        BBCode.parse("[quote]Hello World![/quote]")
        .run {
            assertQuote(elements.at(0)) {
                assertText(elements.at(0), value="Hello World!")
            }
        }
    }

    @Test
    public fun parse470600465() {
        BBCode.parse("[quote][/quote]")
        .run {
            assertQuote(elements.at(0)) {
                kotlin.test.assertEquals(0, elements.size)
            }
        }
    }

    @Test
    public fun parse1788776377() {
        BBCode.parse("[quote] [/quote]")
        .run {
            assertQuote(elements.at(0)) {
                assertText(elements.at(0), value=" ")
            }
        }
    }

    @Test
    public fun parse1294424479() {
        BBCode.parse("[quote] /[][/]Hello [/quote]")
        .run {
            assertQuote(elements.at(0)) {
                assertText(elements.at(0), value=" /[][/]Hello ")
            }
        }
    }

    @Test
    public fun parse1141258730() {
        BBCode.parse("[code]Hello World![/code]")
        .run {
            assertText(elements.at(0), value="Hello World!", code=true)
        }
    }

    @Test
    public fun parse496956199() {
        BBCode.parse("[code][/code]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse1670130897() {
        BBCode.parse("[code] [/code]")
        .run {
            assertText(elements.at(0), value=" ", code=true)
        }
    }

    @Test
    public fun parse1274924885() {
        BBCode.parse("[code] /[][/]Hello [/code]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", code=true)
        }
    }

    @Test
    public fun parse995222602() {
        BBCode.parse("[mask]Hello World![/mask]")
        .run {
            assertText(elements.at(0), value="Hello World!", mask=true)
        }
    }

    @Test
    public fun parse276603911() {
        BBCode.parse("[mask][/mask]")
        .run {
            kotlin.test.assertEquals(0, elements.size)
        }
    }

    @Test
    public fun parse1130804845() {
        BBCode.parse("[mask] [/mask]")
        .run {
            assertText(elements.at(0), value=" ", mask=true)
        }
    }

    @Test
    public fun parse1772059667() {
        BBCode.parse("[mask] /[][/]Hello [/mask]")
        .run {
            assertText(elements.at(0), value=" /[][/]Hello ", mask=true)
        }
    }
}


// @formatter:on