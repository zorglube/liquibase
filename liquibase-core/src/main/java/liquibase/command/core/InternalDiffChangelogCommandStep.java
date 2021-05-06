package liquibase.command.core;

import liquibase.Scope;
import liquibase.command.*;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.diff.DiffResult;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.util.StringUtil;

import java.io.PrintStream;

public class InternalDiffChangelogCommandStep extends InternalDiffCommandStep {

    public static final String[] COMMAND_NAME = {"internalDiffChangelog"};

    public static final CommandArgumentDefinition<String> CHANGELOG_FILE_ARG;
    public static final CommandArgumentDefinition<DiffOutputControl> DIFF_OUTPUT_CONTROL_ARG;

    static {
        final CommandBuilder builder = new CommandBuilder(COMMAND_NAME);

        CHANGELOG_FILE_ARG = builder.argument("changelogFile", String.class).required().build();
        DIFF_OUTPUT_CONTROL_ARG = builder.argument("diffOutputControl", DiffOutputControl.class).required().build();
    }


    @Override
    public String[] getName() {
        return COMMAND_NAME;
    }

    @Override
    public void adjustCommandDefinition(CommandDefinition commandDefinition) {
        super.adjustCommandDefinition(commandDefinition);
        commandDefinition.setInternal(true);
    }

    @Override
    public void run(CommandResultsBuilder resultsBuilder) throws Exception {
        CommandScope commandScope = resultsBuilder.getCommandScope();

        Database referenceDatabase = commandScope.getArgumentValue(REFERENCE_DATABASE_ARG);
        String changeLogFile = commandScope.getArgumentValue(CHANGELOG_FILE_ARG);

        InternalSnapshotCommandStep.logUnsupportedDatabase(referenceDatabase, this.getClass());

        DiffResult diffResult = createDiffResult(commandScope);

        PrintStream outputStream = new PrintStream(resultsBuilder.getOutputStream());

        outputBestPracticeMessage();

        ObjectQuotingStrategy originalStrategy = referenceDatabase.getObjectQuotingStrategy();
        try {
            referenceDatabase.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
            if (StringUtil.trimToNull(changeLogFile) == null) {
                createDiffToChangeLogObject(diffResult, commandScope).print(outputStream);
            } else {
                createDiffToChangeLogObject(diffResult, commandScope).print(changeLogFile);
            }
        }
        finally {
            referenceDatabase.setObjectQuotingStrategy(originalStrategy);
            outputStream.flush();
        }
        resultsBuilder.addResult("statusCode", 0);
    }

    protected DiffToChangeLog createDiffToChangeLogObject(DiffResult diffResult, CommandScope commandScope) {
        return new DiffToChangeLog(diffResult, commandScope.getArgumentValue(DIFF_OUTPUT_CONTROL_ARG));
    }


    protected void outputBestPracticeMessage() {
        Scope.getCurrentScope().getUI().sendMessage(
           "BEST PRACTICE: The changelog generated by diffChangeLog/generateChangeLog should be " +
           "inspected for correctness and completeness before being deployed.");
    }

}
