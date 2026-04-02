package riven.core.service.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NicknameExpanderTest {

    // ------ expand() ------

    @Nested
    inner class Expand {

        @Test
        fun `expand william returns bill will willy liam`() {
            val result = NicknameExpander.expand("william")
            assertTrue("bill" in result, "Expected 'bill' in result: $result")
            assertTrue("will" in result, "Expected 'will' in result: $result")
            assertTrue("willy" in result, "Expected 'willy' in result: $result")
            assertTrue("liam" in result, "Expected 'liam' in result: $result")
        }

        @Test
        fun `expand william does not include william itself`() {
            val result = NicknameExpander.expand("william")
            assertFalse("william" in result, "Expected 'william' NOT in result: $result")
        }

        @Test
        fun `expand bill returns william bidirectionally`() {
            val result = NicknameExpander.expand("bill")
            assertTrue("william" in result, "Expected 'william' in result (bidirectional): $result")
        }

        @Test
        fun `expand is case insensitive`() {
            val lower = NicknameExpander.expand("william")
            val upper = NicknameExpander.expand("WILLIAM")
            val mixed = NicknameExpander.expand("William")
            assertEquals(lower, upper)
            assertEquals(lower, mixed)
        }

        @Test
        fun `expand returns empty set for unknown name`() {
            val result = NicknameExpander.expand("xyznotaname")
            assertTrue(result.isEmpty(), "Expected empty set for unknown name, got: $result")
        }

        @Test
        fun `expand returns empty set for empty string`() {
            val result = NicknameExpander.expand("")
            assertTrue(result.isEmpty(), "Expected empty set for empty string, got: $result")
        }

        @Test
        fun `at least 100 distinct names produce non-empty expand results`() {
            // Validates that ~150 groups are actually populated with sufficient coverage
            val sampleNames = listOf(
                "william", "bill", "robert", "bob", "richard", "dick", "james", "jim",
                "john", "jack", "charles", "charlie", "thomas", "tom", "michael", "mike",
                "edward", "ted", "henry", "hank", "joseph", "joe", "christopher", "chris",
                "daniel", "dan", "matthew", "matt", "anthony", "tony", "andrew", "andy",
                "nicholas", "nick", "donald", "don", "stephen", "steve", "mark", "marc",
                "patricia", "pat", "elizabeth", "beth", "jennifer", "jenny", "margaret", "peggy",
                "catherine", "kate", "susan", "sue", "dorothy", "dot", "rebecca", "becky",
                "virginia", "ginny", "barbara", "barb", "alice", "ali", "abigail", "abby",
                "alexander", "alex", "alfred", "alf", "ann", "annie", "benjamin", "ben",
                "carl", "charles", "chester", "chet", "constance", "connie", "david", "dave",
                "deborah", "deb", "eleanor", "nell", "emily", "emmy", "ernest", "ernie",
                "florence", "flo", "frances", "fran", "gabriel", "gabe", "george", "georgie",
                "gerald", "gerry", "gertrude", "gertie", "grace", "gracie", "gregory", "greg",
                "harold", "harry", "harriet", "hattie", "helen", "nell", "herbert", "herb",
                "irene", "rene", "jacob", "jake", "jacqueline", "jackie", "jessica", "jess",
                "judith", "judy", "julia", "jules", "karen", "kay", "katherine", "cathy",
                "leonard", "len", "margaret", "meg", "martin", "marty", "mary", "molly",
                "melissa", "mel", "montgomery", "monty", "nancy", "nan", "natalie", "nat",
                "nathaniel", "nate", "oliver", "ollie", "pamela", "pam", "penelope", "penny",
                "peter", "pete", "philip", "phil", "raymond", "ray", "reginald", "reg",
                "samuel", "sam", "sarah", "sal", "sharon", "shari", "simon", "si",
                "teresa", "tess", "timothy", "tim", "tobias", "toby", "walter", "walt",
                "zachary", "zach",
            )
            val nonEmptyCount = sampleNames.count { NicknameExpander.expand(it).isNotEmpty() }
            assertTrue(nonEmptyCount >= 100, "Expected at least 100 names to produce non-empty expand results, got $nonEmptyCount")
        }
    }

    // ------ areEquivalent() ------

    @Nested
    inner class AreEquivalent {

        @Test
        fun `william and bill are equivalent`() {
            assertTrue(NicknameExpander.areEquivalent("william", "bill"))
        }

        @Test
        fun `bill and william are equivalent symmetrically`() {
            assertTrue(NicknameExpander.areEquivalent("bill", "william"))
        }

        @Test
        fun `william and robert are not equivalent`() {
            assertFalse(NicknameExpander.areEquivalent("william", "robert"))
        }

        @Test
        fun `unknown names are not equivalent`() {
            assertFalse(NicknameExpander.areEquivalent("unknown1", "unknown2"))
        }

        @Test
        fun `known name and unknown name are not equivalent`() {
            assertFalse(NicknameExpander.areEquivalent("william", "xyznotaname"))
        }

        @Test
        fun `areEquivalent is case insensitive`() {
            assertTrue(NicknameExpander.areEquivalent("WILLIAM", "BILL"))
            assertTrue(NicknameExpander.areEquivalent("William", "Bill"))
        }
    }
}
