package liquibase.extension.testing.command

CommandTests.define {
    command = ["futureRollbackSql"]
    signature = """
Short Description: Generate the raw SQL needed to rollback undeployed changes
Long Description: NOT SET
Required Args:
  url (String) The JDBC Database connection URL
Optional Args:
  changelogFile (String) The root changelog
    Default: null
  contexts (String) Changeset contexts to match
    Default: null
  labels (String) Changeset labels to match
    Default: null
  password (String) Password to use to connect to the database
    Default: null
  username (String) Username to use to connect to the database
    Default: null
"""

    run {
        arguments = [
                changelogFile: "changelogs/hsqldb/complete/rollback.changelog.xml",
        ]

        setup {
            runChangelog "changelogs/hsqldb/complete/rollback.changelog.xml"
            rollback 5, "changelogs/hsqldb/complete/rollback.changelog.xml"

        }

        expectedResults = [
                statusCode   : 0
        ]
    }
}
