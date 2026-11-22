package com.margomarket.catalog;

/** How a world's market relates to others when comparing prices. */
public enum WorldType {
    /** Public world with character transfers; all of them form one shared market. */
    TRANSFERABLE,
    /** Public world without character transfers; its own market. */
    ISOLATED,
    /** Private or special world; its own market. */
    PRIVATE
}
