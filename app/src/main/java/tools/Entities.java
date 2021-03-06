package tools;

import java.util.Hashtable;

/*
 This class supplies basic methods to escape / unescape special chars according XML specifications

 This class was derived from Apache class included in https://commons.apache.org/proper/commons-lang/
*/
public final class Entities {

    private static final String[][] BASIC_ARRAY = {
        {"quot" , "34"}, // " - double-quote
        {"amp"  , "38"}, // & - ampersand
        {"lt"   , "60"}, // < - less-than
        {"gt"   , "62"}, // > - greater-than
        {"apos" , "39"}, // XML apostrophe
    };

    /**
     * <p>The set of entities supported by standard XML.</p>
     */
    public static final Entities XML;

    static {
        XML = new Entities();
        XML.addEntities(BASIC_ARRAY);
    }

    interface EntityMap {
        void add(String name, int value);
        String name(int value);
        int value(String name);
    }

    static class PrimitiveEntityMap implements EntityMap {
        private final Hashtable<String, Integer> mapNameToValue = new Hashtable<>();
        private final Hashtable<Integer, String> mapValueToName = new Hashtable<>();

        public void add(String name, int value) {
            mapNameToValue.put(name, value);
            mapValueToName.put(value, name);
        }

        public String name(int value) {
            return mapValueToName.get(value);
        }

        public int value(String name) {
        	Integer value = mapNameToValue.get(name);
            if (value == null) {
                return -1;
            }
            return value;
        }
    }

    static class LookupEntityMap extends PrimitiveEntityMap {

        private String[] lookupTable;
        private final int LOOKUP_TABLE_SIZE = 256;

        public String name(int value) {
            if (value < LOOKUP_TABLE_SIZE) {
                return lookupTable()[value];
            }
            return super.name(value);
        }

        private String[] lookupTable() {
            if (lookupTable == null) {
                createLookupTable();
            }
            return lookupTable;
        }

        private void createLookupTable() {
            lookupTable = new String[LOOKUP_TABLE_SIZE];
            for (int i = 0; i < LOOKUP_TABLE_SIZE; ++i) {
                lookupTable[i] = super.name(i);
            }
        }
    }

    private final EntityMap map = new Entities.LookupEntityMap();

    private void addEntities(String[][] entityArray) {
        for (String[] anEntityArray : entityArray) {
            map.add(anEntityArray[0], Integer.parseInt(anEntityArray[1]));
        }
    }

    public String escape(String str) {
        char          ch;
        String        entityName;
        StringBuilder  buf;
        int           intValue;

        buf = new StringBuilder(str.length() * 2);

        for (int i = 0, l = str.length(); i < l; ++i) {
            ch = str.charAt(i);
            entityName = map.name(ch);

            if (entityName == null) {
                if (ch > 0x7F) {
                    intValue = ch;
                    buf.append("&#");
                    buf.append(intValue);
                    buf.append(';');
                } else {
                    buf.append(ch);
                }
            } else {
                buf.append('&');
                buf.append(entityName);
                buf.append(';');
            }
        }
        return buf.toString();
    }

    public String unescape(StringBuilder str) {
        StringBuilder  buf;
        String        entityName;
        char          ch, charAt1;
        int           entityValue;

        buf = new StringBuilder(str.length());

        for (int i = 0, l = str.length(); i < l; ++i) {
            ch = str.charAt(i);
            if (ch == '&') {
                int semi = str.indexOf(";", i + 1);
                if (semi == -1) {
                    buf.append(ch);
                    continue;
                }
                entityName = str.substring(i + 1, semi);
                if (entityName.charAt(0) == '#') {
                    charAt1 = entityName.charAt(1);
                    if (charAt1 == 'x' || charAt1=='X') {
                        entityValue = Integer.valueOf(entityName.substring(2), 16);
                    } else {
                        entityValue = Integer.parseInt(entityName.substring(1));
                    }
                } else {
                    entityValue = map.value(entityName);
                } if (entityValue == -1) {
                    buf.append('&');
                    buf.append(entityName);
                    buf.append(';');
                } else {
                    buf.append((char) (entityValue));
                }
                i = semi;
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }
    
    public String unescape(String str) {
        StringBuilder  buf;
        String        entityName;
        char          ch, charAt1;
        int           entityValue;

        buf = new StringBuilder(str.length());

        for (int i = 0, l = str.length(); i < l; ++i) {
            ch = str.charAt(i);
            if (ch == '&') {
                int semi = str.indexOf(';', i + 1);
                if (semi == -1) {
                    buf.append(ch);
                    continue;
                }
                entityName = str.substring(i + 1, semi);
                if (entityName.charAt(0) == '#') {
                    charAt1 = entityName.charAt(1);
                    if (charAt1 == 'x' || charAt1=='X') {
                        entityValue = Integer.valueOf(entityName.substring(2), 16);
                    } else {
                        entityValue = Integer.parseInt(entityName.substring(1));
                    }
                } else {
                    entityValue = map.value(entityName);
                } if (entityValue == -1) {
                    buf.append('&');
                    buf.append(entityName);
                    buf.append(';');
                } else {
                    buf.append((char) (entityValue));
                }
                i = semi;
            } else {
                buf.append(ch);
            }
        }
        return buf.toString();
    }
}
