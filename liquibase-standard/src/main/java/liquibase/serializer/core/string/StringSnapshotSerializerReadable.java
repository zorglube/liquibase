package liquibase.serializer.core.string;

import liquibase.GlobalConfiguration;
import liquibase.database.Database;
import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.serializer.LiquibaseSerializable;
import liquibase.serializer.SnapshotSerializer;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.structure.CatalogLevelObject;
import liquibase.structure.DatabaseLevelObject;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.*;
import liquibase.util.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

public class StringSnapshotSerializerReadable implements SnapshotSerializer {

    private static final int INDENT_LENGTH = 4;
    private static final Set<Class<?>> SKIP_DISPLAYING_TYPES = new HashSet<>(Arrays.asList(Schema.class, Catalog.class, Column.class));
    private static final Set<Class<?>> EXPAND_NESTED_TYPES = new HashSet<>(Arrays.asList(Table.class, View.class));

    @Override
    public String[] getValidFileExtensions() {
        return new String[]{"txt"};
    }

    @Override
    public String serialize(LiquibaseSerializable object, boolean pretty) {
        try {
            StringBuilder buffer = new StringBuilder();
            DatabaseSnapshot snapshot = ((DatabaseSnapshot) object);
            Database database = snapshot.getDatabase();

            buffer.append("Database snapshot for ").append(database.getConnection().getURL()).append("\n");
            addDivider(buffer);
            buffer.append("Database type: ").append(database.getDatabaseProductName()).append("\n");
            buffer.append("Database version: ").append(database.getDatabaseProductVersion()).append("\n");
            buffer.append("Database user: ").append(database.getConnection().getConnectionUserName()).append("\n");

            SnapshotControl snapshotControl = snapshot.getSnapshotControl();
            List<Class> includedTypes = sort(snapshotControl.getTypesToInclude());

            buffer.append("Included types:\n").append(StringUtil.indent(StringUtil.join(includedTypes, "\n", (StringUtil.StringUtilFormatter<Class>) Class::getName))).append("\n");


            List<Schema> schemas = sort(snapshot.get(Schema.class), Comparator.comparing(Schema::toString));

            for (Schema schema : schemas) {
                if (database.supports(Schema.class)) {
                    buffer.append("\nCatalog & Schema: ").append(schema.getCatalogName()).append(" / ").append(schema.getName()).append("\n");
                } else {
                    buffer.append("\nCatalog: ").append(schema.getCatalogName()).append("\n");
                }

                StringBuilder catalogBuffer = new StringBuilder();
                for (Class type : includedTypes) {
                    if (shouldSkipDisplayingType(type)) {
                        continue;
                    }
                    List<DatabaseObject> objects = new ArrayList<DatabaseObject>(snapshot.get(type));
                    ListIterator<DatabaseObject> iterator = objects.listIterator();
                    while (iterator.hasNext()) {
                        DatabaseObject next = iterator.next();
                        if (next instanceof DatabaseLevelObject) {
                            continue;
                        }

                        Schema objectSchema = next.getSchema();
                        if (objectSchema == null) {
                            if (!(next instanceof CatalogLevelObject) || !((CatalogLevelObject) next).getCatalog().equals(schema.getCatalog())) {
                                iterator.remove();
                            }
                        } else if (!objectSchema.equals(schema)) {
                            iterator.remove();
                        }
                    }
                    outputObjects(objects, type, catalogBuffer);
                }
                buffer.append(StringUtil.indent(catalogBuffer.toString(), INDENT_LENGTH));

            }

            return buffer.toString().replace("\r\n", "\n").replace("\r", "\n"); //standardize all newline chars

        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }

    }

    private boolean shouldSkipDisplayingType(Class<?> type) {
        return this.getSkipDisplayingTypes().stream().anyMatch(type::isAssignableFrom);
    }

    /**
     * We need this method with protected access modifier to be able to extend this set from PRO and/or from extensions.
     * These types should not be displayed as they are either processed separately like {@link Catalog} and
     * {@link Schema}, or they are inner entities of the types-containers like {@link Column}-s
     * to {@link Table}-s and {@link View}-s
     */
    protected Set<Class<?>> getSkipDisplayingTypes() {
        return SKIP_DISPLAYING_TYPES;
    }

    protected void outputObjects(List objects, Class type, StringBuilder catalogBuffer) {
        List<? extends DatabaseObject> databaseObjects = sort(objects);
        if (!databaseObjects.isEmpty()) {
            catalogBuffer.append(type.getName()).append(":\n");

            StringBuilder typeBuffer = new StringBuilder();
            for (DatabaseObject databaseObject : databaseObjects) {
                typeBuffer.append(databaseObject.getName()).append("\n");
                typeBuffer.append(StringUtil.indent(serialize(databaseObject, null), INDENT_LENGTH)).append("\n");
            }

            catalogBuffer.append(StringUtil.indent(typeBuffer.toString(), INDENT_LENGTH)).append("\n");
        }
    }

    private String serialize(final DatabaseObject databaseObject, final DatabaseObject parentObject) {

        StringBuilder buffer = new StringBuilder();

        final List<String> attributes = sort(databaseObject.getAttributes());
        for (String attribute : attributes) {
            if ("name".equals(attribute)) {
                continue;
            }
            if ("schema".equals(attribute)) {
                continue;
            }
            if ("catalog".equals(attribute)) {
                continue;
            }
            Object value = databaseObject.getAttribute(attribute, Object.class);

            if (value instanceof Schema) {
                continue;
            }

            if (value instanceof DatabaseObject) {
                if (
                        (parentObject != null)
                                && ((DatabaseObject) value).getSnapshotId() != null
                                && ((DatabaseObject) value).getSnapshotId().equals(parentObject.getSnapshotId())
                ) {
                    continue;
                }

                boolean expandContainedObjects = shouldExpandNestedObject(databaseObject);

                if (expandContainedObjects) {
                    value = ((DatabaseObject) value).getName() + "\n" + StringUtil.indent(serialize((DatabaseObject) value, databaseObject), INDENT_LENGTH);
                } else {
                    value = databaseObject.getSerializableFieldValue(attribute);
                }
            } else if (value instanceof Collection) {
                if (((Collection<?>) value).isEmpty()) {
                    value = null;
                } else {
                    if (((Collection) value).iterator().next() instanceof DatabaseObject) {
                        value = StringUtil.join(new TreeSet<>((Collection<DatabaseObject>) value), "\n", obj -> {
                            if (obj instanceof DatabaseObject) {
                                if (shouldExpandNestedObject(databaseObject)) {
                                    return ((DatabaseObject) obj).getName() + "\n" + StringUtil.indent(serialize(((DatabaseObject) obj), databaseObject), INDENT_LENGTH);
                                } else {
                                    return ((DatabaseObject) obj).getName();
                                }
                            } else {
                                return obj.toString();
                            }
                        });
                        value = "\n" + StringUtil.indent((String) value, INDENT_LENGTH);
                    } else {
                        value = databaseObject.getSerializableFieldValue(attribute);
                    }
                }
            } else {
                value = databaseObject.getSerializableFieldValue(attribute);
            }
            if (value != null) {
                buffer.append(attribute).append(": ").append(value).append("\n");
            }
        }

        return buffer.toString().replaceFirst("\n$", "");

    }

    protected boolean shouldExpandNestedObject(DatabaseObject container) {
        return this.getExpandNestedTypes().stream().anyMatch(container.getClass()::isAssignableFrom);
    }

    /**
     * We need this method with protected access modifier to be able to extend this set from PRO and/or from extensions.
     * These types should not be process like regular {@link DatabaseObject}-s as their full structure meters.
     * Usually these types are type-containers like {@link Table} and {@link View}.
     */
    protected Set<Class<?>> getExpandNestedTypes() {
        return EXPAND_NESTED_TYPES;
    }

    protected void addDivider(StringBuilder buffer) {
        buffer.append("-----------------------------------------------------------------\n");
    }

    private List sort(Collection objects) {
        return sort(objects, (Comparator) (o1, o2) -> {
            if (o1 instanceof Comparable) {
                return ((Comparable) o1).compareTo(o2);
            } else if (o1 instanceof Class) {
                return ((Class<?>) o1).getName().compareTo(((Class<?>) o2).getName());
            } else {
                throw new ClassCastException(o1.getClass().getName() + " cannot be cast to java.lang.Comparable or java.lang.Class");
            }
        });
    }

    private <T> List<T> sort(Collection objects, Comparator<T> comparator) {
        List returnList = new ArrayList(objects);
        returnList.sort(comparator);

        return returnList;
    }

    @Override
    public void write(DatabaseSnapshot snapshot, OutputStream out) throws IOException {
        out.write(serialize(snapshot, true).getBytes(GlobalConfiguration.OUTPUT_FILE_ENCODING.getCurrentValue()));
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }
}
