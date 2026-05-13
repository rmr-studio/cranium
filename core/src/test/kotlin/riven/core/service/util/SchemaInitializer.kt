package cranium.core.service.util

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource

/**
 * Initializes a test database with the production SQL schema from db/schema/.
 *
 * Executes SQL files in the documented dependency order (see db/schema/README.md).
 * Used by integration test base classes to get a production-identical schema in Testcontainers PostgreSQL.
 *
 * Skips RLS policies and grants since they reference Supabase-specific roles
 * (authenticated, anon, supabase_auth_admin) that don't exist in the test database.
 */
object SchemaInitializer {

    private val logger = LoggerFactory.getLogger(SchemaInitializer::class.java)

    /**
     * Table files in FK-dependency order. Files not listed here are executed alphabetically after.
     */
    private val TABLE_ORDER = listOf(
        "workspace.sql",
        "user.sql",
        "activity.sql",
        "catalog.sql",
        "entities.sql",
        "integrations.sql",
        "blocks.sql",
        "identity.sql",
        "notes.sql",
        "notification.sql",
        "workflow.sql",
    )

    /**
     * Directories to execute, in order. Skips 05_rls and 09_grants (Supabase-specific roles).
     */
    private val SCHEMA_DIRECTORIES = listOf(
        "00_extensions",
        "01_tables",
        "02_indexes",
        "03_functions",
        "04_constraints",
        "08_triggers",
    )

    /**
     * Initialize the database schema from SQL files.
     *
     * @param dataSource the DataSource connected to the test database
     * @param schemaDir path to the db/schema/ directory (defaults to auto-detection from project root)
     */
    fun initializeSchema(dataSource: DataSource, schemaDir: Path = detectSchemaDir()) {
        System.err.println("[SchemaInitializer] Initializing test database schema from: $schemaDir")
        logger.info("Initializing test database schema from: {}", schemaDir)

        val connection = dataSource.connection
        connection.use { conn ->
            val statement = conn.createStatement()
            statement.use { stmt ->
                // Disable FK checks during schema creation to handle circular dependencies
                stmt.execute("SET session_replication_role = replica")

                for (directory in SCHEMA_DIRECTORIES) {
                    val dirPath = schemaDir.resolve(directory)
                    if (!Files.isDirectory(dirPath)) {
                        logger.debug("Skipping missing directory: {}", directory)
                        continue
                    }

                    val sqlFiles = getOrderedFiles(directory, dirPath)
                    var pending = sqlFiles.map { it to Files.readString(it) }
                    var pass = 0

                    // Retry loop: keep executing until no more progress (handles circular FK deps)
                    while (pending.isNotEmpty()) {
                        pass++
                        val stillFailing = mutableListOf<Pair<Path, String>>()

                        for ((file, sql) in pending) {
                            try {
                                executeStatements(stmt, sql)
                                val label = if (pass == 1) "OK" else "OK (pass $pass)"
                                println("[SchemaInitializer] $label: $directory/${file.fileName}")
                            } catch (e: Exception) {
                                stillFailing.add(file to sql)
                                if (pass == 1) {
                                    println("[SchemaInitializer] DEFERRED: $directory/${file.fileName}: ${e.message}")
                                }
                            }
                        }

                        if (stillFailing.size == pending.size) {
                            // No progress made — log remaining failures and break
                            for ((file, sql) in stillFailing) {
                                try { executeStatements(stmt, sql) } catch (e: Exception) {
                                    println("[SchemaInitializer] FAIL: $directory/${file.fileName}: ${e.message}")
                                }
                            }
                            break
                        }

                        pending = stillFailing
                    }
                }

                // Re-enable FK checks
                stmt.execute("SET session_replication_role = DEFAULT")
            }
        }

        logger.info("Test database schema initialization complete")
    }

    /**
     * Execute a SQL script by splitting into individual statements on semicolons.
     * Statements within function bodies (delimited by $$) are preserved.
     * Throws on the first statement failure but continues executing remaining statements.
     */
    private fun executeStatements(stmt: java.sql.Statement, sql: String) {
        val statements = splitSqlStatements(sql)
        var firstException: Exception? = null

        for (statement in statements) {
            try {
                stmt.execute(statement)
            } catch (e: Exception) {
                if (firstException == null) firstException = e
            }
        }

        if (firstException != null) throw firstException
    }

    /**
     * Split SQL into individual statements, respecting $$ function body delimiters.
     */
    private fun splitSqlStatements(sql: String): List<String> {
        val results = mutableListOf<String>()
        var inDollarQuote = false
        var inLineComment = false
        val currentStatement = StringBuilder()

        var i = 0
        while (i < sql.length) {
            // Handle line comments: skip semicolons inside -- comments
            if (!inDollarQuote && !inLineComment && sql[i] == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                inLineComment = true
                currentStatement.append("--")
                i += 2
                continue
            }

            if (inLineComment) {
                currentStatement.append(sql[i])
                if (sql[i] == '\n') inLineComment = false
                i++
                continue
            }

            if (sql[i] == '$' && i + 1 < sql.length && sql[i + 1] == '$') {
                inDollarQuote = !inDollarQuote
                currentStatement.append("$$")
                i += 2
                continue
            }

            if (sql[i] == ';' && !inDollarQuote) {
                val stmt = currentStatement.toString().trim()
                if (stmt.isNotBlank()) results.add(stmt)
                currentStatement.clear()
                i++
                continue
            }

            currentStatement.append(sql[i])
            i++
        }

        val remaining = currentStatement.toString().trim()
        if (remaining.isNotBlank()) results.add(remaining)

        return results
    }

    /**
     * Get SQL files for a directory in the correct execution order.
     * For 01_tables, uses the defined dependency order. Other directories use alphabetical order.
     */
    private fun getOrderedFiles(directoryName: String, dirPath: Path): List<Path> {
        val allFiles = Files.list(dirPath)
            .filter { it.toString().endsWith(".sql") }
            .toList()

        if (directoryName == "01_tables") {
            val filesByName = allFiles.associateBy { it.fileName.toString() }
            val ordered = mutableListOf<Path>()

            // Add files in defined order first
            for (fileName in TABLE_ORDER) {
                filesByName[fileName]?.let { ordered.add(it) }
            }

            // Add remaining files alphabetically
            val remaining = allFiles.filter { it.fileName.toString() !in TABLE_ORDER.toSet() }
                .sortedBy { it.fileName.toString() }
            ordered.addAll(remaining)

            return ordered
        }

        return allFiles.sortedBy { it.fileName.toString() }
    }

    /**
     * Auto-detect the db/schema/ directory by walking up from the working directory.
     */
    private fun detectSchemaDir(): Path {
        var current = Paths.get("").toAbsolutePath()
        repeat(5) {
            val candidate = current.resolve("db/schema")
            if (Files.isDirectory(candidate)) return candidate
            current = current.parent ?: return@repeat
        }
        throw IllegalStateException(
            "Could not find db/schema/ directory. " +
                "Pass the path explicitly to SchemaInitializer.initializeSchema()"
        )
    }
}
