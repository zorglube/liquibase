package liquibase.extension.testing.command

CommandTests.define {
    command = ["migrate"]
    signature = """
Short Description: Deploys changes from the changelog file that have not yet been deployed
Long Description: NOT SET
Required Args:
  url (String) The JDBC database connection URL
Optional Args:
  changelogFile (String) The root changelog
    Default: null
  contexts (String) Context string to use for filtering which changes to migrate
    Default: null
  labels (String) Label expression to use for filtering which changes to migrate
    Default: null
  password (String) Password to use to connect to the database
    Default: null
  username (String) Username to use to connect to the database
    Default: null
"""
    run {
        arguments = [
                changelogFile: "changelogs/hsqldb/complete/simple.changelog.xml",
        ]

        expectedResults = [
                statusCode   : 0
        ]
    }
}
