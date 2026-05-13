package cranium.core.service.identity

/**
 * Bidirectional English nickname lookup utility.
 *
 * Contains ~150 name groups covering common English nicknames and their variants.
 * All lookups are case-insensitive. This is a pure stateless Kotlin object — not a Spring bean.
 *
 * Usage:
 * - `expand("william")` returns `{"bill", "will", "willy", "liam"}` (variants, excluding input)
 * - `areEquivalent("william", "bill")` returns `true`
 * - `expand("unknownname")` returns `emptySet()`
 */
object NicknameExpander {

    // ------ Nickname groups ------

    /**
     * Each set contains all known variants for a single name identity.
     * All entries are lowercase. The bidirectional INDEX is built from these groups at init.
     */
    private val GROUPS: List<Set<String>> = listOf(
        // A
        setOf("abigail", "abby", "abbie", "gail"),
        setOf("abraham", "abe", "bram"),
        setOf("adam", "ad"),
        setOf("adelaide", "addie", "ada", "adele"),
        setOf("agatha", "aggie"),
        setOf("agnes", "aggie", "nessie"),
        setOf("albert", "al", "bert", "albie"),
        setOf("alexander", "alex", "alec", "al", "xander", "sasha"),
        setOf("alfred", "al", "alf", "fred", "freddie"),
        setOf("alice", "ali", "allie"),
        setOf("alicia", "ali", "allie", "alice"),
        setOf("allen", "al"),
        setOf("ambrose", "brose"),
        setOf("amelia", "amy", "millie", "mia"),
        setOf("anastasia", "ana", "stacy", "annie"),
        setOf("andrew", "andy", "drew"),
        setOf("angela", "angie", "angel"),
        setOf("ann", "annie", "anna", "anne"),
        setOf("anna", "annie", "ann", "anne"),
        setOf("anne", "annie", "ann", "anna"),
        setOf("anthony", "tony", "ant"),
        setOf("arabella", "bella", "ara", "belle"),
        setOf("archibald", "archie", "arch"),
        setOf("arnold", "arnie"),
        setOf("arthur", "art", "artie"),
        setOf("augustine", "gus", "auggie"),
        // B
        setOf("barbara", "barb", "babs", "bobbie"),
        setOf("barnabas", "barney", "barn"),
        setOf("bartholomew", "bart", "barry"),
        setOf("beatrice", "bea", "trixie", "trish"),
        setOf("benjamin", "ben", "benny", "benji"),
        setOf("bernadette", "bernie", "berni", "detta"),
        setOf("bernard", "bernie", "bern"),
        setOf("bertha", "bert", "bertie"),
        setOf("bethany", "beth", "bette"),
        setOf("beverly", "bev"),
        setOf("bradford", "brad"),
        setOf("bradley", "brad"),
        setOf("brandon", "brad"),
        setOf("bridget", "brid", "biddie"),
        // C
        setOf("caleb", "cal"),
        setOf("calvin", "cal"),
        setOf("carolyn", "carol", "carrie", "caro"),
        setOf("caroline", "carol", "carrie", "caro", "lina"),
        setOf("cassandra", "cassie", "cass", "sandy"),
        setOf("catherine", "kate", "cathy", "cat", "katie", "kitty"),
        setOf("cecilia", "cece", "ceil"),
        setOf("charles", "charlie", "chuck", "chas"),
        setOf("charlotte", "charlie", "lottie", "lotte", "char"),
        setOf("chester", "chet"),
        setOf("christian", "chris", "kit"),
        setOf("christina", "chris", "christie", "tina"),
        setOf("christopher", "chris", "kit", "topher"),
        setOf("clarence", "clary"),
        setOf("clementine", "clemmie", "clem"),
        setOf("clifford", "cliff"),
        setOf("constance", "connie"),
        setOf("cornelius", "neil", "corny"),
        setOf("cynthia", "cindy", "cyndy"),
        // D
        setOf("daniel", "dan", "danny"),
        setOf("daphne", "daph"),
        setOf("david", "dave", "davey"),
        setOf("deborah", "deb", "debbie"),
        setOf("delilah", "dee", "lila"),
        setOf("dennis", "denny", "den"),
        setOf("derek", "derry"),
        setOf("diana", "di", "dee"),
        setOf("dominic", "dom", "nic"),
        setOf("donald", "don", "donnie"),
        setOf("dorothy", "dot", "dottie", "dora"),
        setOf("douglas", "doug"),
        // E
        setOf("edgar", "ed", "eddie"),
        setOf("edith", "edie", "ed"),
        setOf("edmund", "ed", "eddie", "ned"),
        setOf("edward", "ed", "eddie", "ned", "ted", "teddy"),
        setOf("eleanor", "ellie", "nell", "nelly", "eleanor", "lenora"),
        setOf("elijah", "eli"),
        setOf("elizabeth", "beth", "liz", "lizzie", "betty", "bette", "eliza", "libby", "elsie"),
        setOf("ella", "ellie", "nell"),
        setOf("elliot", "el"),
        setOf("emily", "emmy", "em", "milly"),
        setOf("ernest", "ernie"),
        setOf("esther", "essie", "hetty"),
        setOf("eugenia", "gene", "genie"),
        setOf("evan", "ev"),
        setOf("evelyn", "evie", "ev"),
        // F
        setOf("florence", "flo", "flora", "flossie"),
        setOf("frances", "fran", "fannie", "franny"),
        setOf("francis", "frank", "fran"),
        setOf("franklin", "frank", "frankie"),
        setOf("fred", "freddie", "freddy"),
        setOf("frederick", "fred", "freddie", "fritz"),
        // G
        setOf("gabriel", "gabe"),
        setOf("genevieve", "ginny", "gen", "viv"),
        setOf("george", "georgie"),
        setOf("georgia", "georgie", "george"),
        setOf("gerald", "gerry", "jerry"),
        setOf("geraldine", "gerry", "jerry", "gerrie"),
        setOf("gertrude", "gertie", "trudy"),
        setOf("gilbert", "gil", "bert"),
        setOf("gladys", "glad"),
        setOf("gloria", "glory"),
        setOf("gordon", "gordie"),
        setOf("grace", "gracie"),
        setOf("gregory", "greg"),
        // H
        setOf("harold", "harry", "hal"),
        setOf("harriet", "hattie", "harry"),
        setOf("harvey", "harv"),
        setOf("helen", "nell", "nellie"),
        setOf("henry", "harry", "hank", "hal"),
        setOf("herbert", "herb", "bert"),
        setOf("hillary", "hilly", "hillie"),
        setOf("homer", "home"),
        setOf("horace", "hory"),
        // I
        setOf("ignatius", "iggy"),
        setOf("irene", "rene"),
        setOf("isaac", "ike", "zac"),
        setOf("isabel", "izzy", "bella", "belle", "bel"),
        setOf("isadora", "izzy", "dora"),
        // J
        setOf("jacob", "jake", "jay"),
        setOf("jacqueline", "jackie", "jacque"),
        setOf("james", "jim", "jimmy", "jamie"),
        setOf("jane", "janie", "jan"),
        setOf("janet", "jan", "janie"),
        setOf("jasper", "jas"),
        setOf("jessica", "jess", "jessie"),
        setOf("john", "jack", "johnny", "jon"),
        setOf("jonathan", "jon", "johnny", "john"),
        setOf("joseph", "joe", "joey", "jo"),
        setOf("josiah", "joe", "jo"),
        setOf("josephine", "jo", "josie", "fifi"),
        setOf("judith", "judy", "jude"),
        setOf("julia", "jules", "julie"),
        setOf("julian", "jules"),
        setOf("julius", "jules"),
        setOf("justine", "justie"),
        // K
        setOf("karen", "kari", "kay"),
        setOf("katherine", "kate", "kathy", "katy", "kitty", "kat"),
        setOf("kathleen", "kate", "kathy", "kath"),
        setOf("kevin", "kev"),
        setOf("kristine", "kris", "kristy"),
        setOf("kristopher", "kris"),
        // L
        setOf("laura", "laurie", "lori"),
        setOf("laurence", "larry", "lars", "law"),
        setOf("lavinia", "vinnie", "lavvy"),
        setOf("lawrence", "larry", "lars", "law"),
        setOf("leonard", "len", "lenny"),
        setOf("leticia", "lettie", "tish"),
        setOf("lewis", "lew"),
        setOf("lilian", "lily", "lillie"),
        setOf("lisa", "liz", "lizzie"),
        setOf("lorraine", "lorrie", "lori"),
        setOf("louisa", "lou", "lucy"),
        setOf("louis", "lou"),
        setOf("lucy", "lu"),
        setOf("lydia", "lidie"),
        // M
        setOf("madeleine", "maddie", "maddy", "lena"),
        setOf("margaret", "peg", "peggy", "meg", "maggie", "marge", "margie", "daisy", "rita"),
        setOf("marilyn", "mary", "mari"),
        setOf("mark", "marc"),
        setOf("martha", "mattie", "marty", "pat"),
        setOf("martin", "marty", "mart"),
        setOf("mary", "molly", "polly", "mae", "mamie"),
        setOf("matthew", "matt", "matty"),
        setOf("melissa", "mel", "missy", "lissa"),
        setOf("michael", "mike", "mick", "mickey"),
        setOf("michelle", "shell", "shelly"),
        setOf("miriam", "mimi", "miri"),
        setOf("mitchell", "mitch"),
        setOf("montgomery", "monty"),
        // N
        setOf("nancy", "nan", "nance"),
        setOf("natalie", "nat", "natty"),
        setOf("nathaniel", "nate", "nat", "nathan"),
        setOf("nicholas", "nick", "nikki", "nicky", "nico"),
        setOf("nicole", "nikki", "nicki", "cole"),
        setOf("nigel", "nige"),
        setOf("noah", "no"),
        setOf("norbert", "norb", "bert"),
        // O
        setOf("oliver", "ollie", "ol"),
        setOf("olivia", "livvy", "olive"),
        // P
        setOf("pamela", "pam", "pammy"),
        setOf("patricia", "pat", "patty", "trish", "tricia"),
        setOf("patrick", "pat", "paddy"),
        setOf("paul", "pauley"),
        setOf("pauline", "paulie", "polly"),
        setOf("penelope", "penny", "pen"),
        setOf("peter", "pete"),
        setOf("philip", "phil", "pip"),
        setOf("phillip", "phil", "pip"),
        setOf("phyllis", "phyl"),
        setOf("priscilla", "prissy", "cilla"),
        // R
        setOf("rachel", "rache"),
        setOf("raymond", "ray"),
        setOf("rebecca", "becky", "becca", "bec"),
        setOf("reginald", "reg", "reggie"),
        setOf("richard", "rick", "ricky", "dick", "rich"),
        setOf("robert", "bob", "bobby", "rob", "robbie"),
        setOf("roderick", "rod", "ricky"),
        setOf("roger", "rog"),
        setOf("roland", "roly"),
        setOf("ronald", "ron", "ronnie"),
        setOf("rosemary", "rosie", "rose"),
        setOf("rosalind", "roz", "ros", "rosie"),
        setOf("ruth", "ruthie"),
        // S
        setOf("samantha", "sam", "sammy"),
        setOf("samuel", "sam", "sammy"),
        setOf("sandra", "sandy", "sandie"),
        setOf("sarah", "sara", "sal", "sallie"),
        setOf("sebastian", "seb", "basil"),
        setOf("sharon", "shari", "sherry"),
        setOf("shirley", "shirl"),
        setOf("simon", "si"),
        setOf("solomon", "sol"),
        setOf("sophia", "sophie", "sophy"),
        setOf("stephanie", "steph", "stevie"),
        setOf("stephen", "steve", "stevie"),
        setOf("steven", "steve", "stevie"),
        setOf("stuart", "stu"),
        setOf("susan", "sue", "suzie", "suzy"),
        setOf("sylvia", "syl", "sylvie"),
        // T
        setOf("teresa", "tess", "terri", "tessie"),
        setOf("theodore", "ted", "teddy", "theo"),
        setOf("theresa", "tess", "terri", "tessie"),
        setOf("thomas", "tom", "tommy"),
        setOf("timothy", "tim", "timmy"),
        setOf("tina", "tine"),
        setOf("tobias", "toby"),
        setOf("travis", "trav"),
        setOf("trevor", "trev"),
        // V
        setOf("valerie", "val"),
        setOf("vera", "vee"),
        setOf("victor", "vic"),
        setOf("victoria", "vic", "vicky", "tori"),
        setOf("vincent", "vince", "vin"),
        setOf("virginia", "ginny", "ginger"),
        setOf("vivian", "viv"),
        // W
        setOf("walter", "walt", "wally"),
        setOf("wendy", "wen"),
        setOf("wilhelmina", "mina", "wilma"),
        setOf("william", "bill", "billy", "will", "willy", "liam"),
        setOf("winifred", "winnie", "freddie"),
        // Z
        setOf("zachary", "zach", "zak"),
    )

    /**
     * Reverse lookup: each name variant maps to its full group set.
     * Built once at object initialization.
     */
    private val INDEX: Map<String, Set<String>> = buildIndex()

    private fun buildIndex(): Map<String, Set<String>> {
        val map = mutableMapOf<String, Set<String>>()
        for (group in GROUPS) {
            for (name in group) {
                // If a name appears in multiple groups, merge them
                val existing = map[name]
                map[name] = if (existing != null) existing + group else group
            }
        }
        return map
    }

    // ------ Public API ------

    /**
     * Returns all known nickname variants for [name], excluding [name] itself.
     *
     * Lookup is case-insensitive. Returns an empty set if the name is not in any group.
     */
    fun expand(name: String): Set<String> {
        val lower = name.lowercase()
        val group = INDEX[lower] ?: return emptySet()
        return group - lower
    }

    /**
     * Returns true if [a] and [b] are known variants of the same name.
     *
     * Case-insensitive. Returns false if either name is unknown.
     */
    fun areEquivalent(a: String, b: String): Boolean {
        val lowerA = a.lowercase()
        val lowerB = b.lowercase()
        val group = INDEX[lowerA] ?: return false
        return lowerB in group
    }
}
