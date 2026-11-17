package com.jobskills.model.enums;

/** Room enum representing the classroom/room numbering system in the ENUVEX system.
 * Rooms are organized by building block (AB, CB, MRI) with specific room number ranges.
 * Format: BLOCK_floor_roomStart-roomEnd (e.g., ABa_101-103, CB_101-105, MRI_101-107)
 */
public enum Room {
    // Block A rooms (101-122)
    ABa_101_103("ABa_101-103"),
    ABb_104_107("ABb_104-107"),
    ABC_108_112("ABC_108-112"),
    ABD_113_122("ABd_113-122");

    // Block B / CB rooms (multiple floors)
    CB_101_105("CB_101-105"),
    CB_201_205("CB_201-205"),
    CB_301_305("CB_301-305"),
    CB_401_405("CB_401-405");

    // MRI rooms
    MRI_101_107("MRI_101-107"),
    MRI_201_212("MRI_201-212"),
    MRI_301_317("MRI_301-317");

    private final String key;

    Room(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static Room fromKey(String key) {
        for (Room room : values()) {
            if (room.key.equals(key)) {
                return room;
            }
        }
        throw new IllegalArgumentException("Invalid room key: " + key);
    }

    public static Room fromRange(String range) {
        for (Room room : values()) {
            if (room.key.contains(range) || room.key.equals(range)) {
                return room;
            }
        }
        throw new IllegalArgumentException("Invalid room range: " + range);
    }
}