package net.dongliu.apk.parser.struct.resource;

import net.dongliu.apk.parser.struct.StringPool;
import net.dongliu.apk.parser.utils.Buffers;
import net.dongliu.apk.parser.utils.ParseUtils;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * @author dongliu
 */
public class Type {

    private String name;
    private short id;

    private Locale locale;

    private StringPool keyStringPool;
    private ByteBuffer buffer;
    private long[] offsets;
    private StringPool stringPool;
    private final ResTableConfig config;

    // see Densities.java for values
    private final int density;

    public Type(TypeHeader header) {
        this.id = header.getId();
        this.config = header.getConfig();
        this.locale = new Locale(config.getLanguage(), config.getCountry());
        this.density = config.getDensity();
    }

    public ResTableConfig getConfig() {
        return config;
    }

    public ResourceEntry getResourceEntry(int id) {
        if (id >= offsets.length) {
            return null;
        }

        if (offsets[id] == TypeHeader.NO_ENTRY) {
            return null;
        }

        // read Resource Entries
        Buffers.position(buffer, offsets[id]);
        return readResourceEntry();
    }

    private ResourceEntry readResourceEntry() {
        long beginPos = buffer.position();
        ResourceEntry resourceEntry = new ResourceEntry();
        // size is always 8(simple), or 16(complex)
        int sizeOrKey = Buffers.readUShort(buffer);
        int entryFlags = Buffers.readUShort(buffer);

        if ((entryFlags & ResourceEntry.FLAG_COMPACT) != 0) {
            // Compact entry (aapt2 --enable-compact-entries): the first uint16 is the key index
            // instead of the entry size, the high byte of the flags is the value's data type and
            // the following uint32 is the value data itself.
            resourceEntry.setSize(8);
            resourceEntry.setFlags(entryFlags);
            resourceEntry.setKey(keyStringPool.get(sizeOrKey));

            int dataType = (entryFlags >> 8) & 0xff;
            int data = buffer.getInt();

            // Re-use the regular Res_value reader by presenting it a synthetic 8 byte Res_value.
            ByteBuffer value = ByteBuffer.allocate(8).order(buffer.order());
            value.putShort((short) 8);
            value.put((byte) 0);
            value.put((byte) dataType);
            value.putInt(data);
            value.flip();
            resourceEntry.setValue(ParseUtils.readResValue(value, stringPool));
            return resourceEntry;
        }

        resourceEntry.setSize(sizeOrKey);
        resourceEntry.setFlags(entryFlags);
        long keyRef = buffer.getInt();
        String key = keyStringPool.get((int) keyRef);
        resourceEntry.setKey(key);

        if ((resourceEntry.getFlags() & ResourceEntry.FLAG_COMPLEX) != 0) {
            ResourceMapEntry resourceMapEntry = new ResourceMapEntry(resourceEntry);

            // Resource identifier of the parent mapping, or 0 if there is none.
            resourceMapEntry.setParent(Buffers.readUInt(buffer));
            resourceMapEntry.setCount(Buffers.readUInt(buffer));

            Buffers.position(buffer, beginPos + resourceEntry.getSize());

            //An individual complex Resource entry comprises an entry immediately followed by one or more fields.
            ResourceTableMap[] resourceTableMaps = new ResourceTableMap[(int) resourceMapEntry.getCount()];
            for (int i = 0; i < resourceMapEntry.getCount(); i++) {
                resourceTableMaps[i] = readResourceTableMap();
            }

            resourceMapEntry.setResourceTableMaps(resourceTableMaps);
            return resourceMapEntry;
        } else {
            Buffers.position(buffer, beginPos + resourceEntry.getSize());
            resourceEntry.setValue(ParseUtils.readResValue(buffer, stringPool));
            return resourceEntry;
        }
    }

    private ResourceTableMap readResourceTableMap() {
        ResourceTableMap resourceTableMap = new ResourceTableMap();
        resourceTableMap.setNameRef(Buffers.readUInt(buffer));
        resourceTableMap.setResValue(ParseUtils.readResValue(buffer, stringPool));

        if ((resourceTableMap.getNameRef() & 0x02000000) != 0) {
            //read arrays
        } else if ((resourceTableMap.getNameRef() & 0x01000000) != 0) {
            // read attrs
        } else {
        }

        return resourceTableMap;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public short getId() {
        return id;
    }

    public void setId(short id) {
        this.id = id;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public StringPool getKeyStringPool() {
        return keyStringPool;
    }

    public void setKeyStringPool(StringPool keyStringPool) {
        this.keyStringPool = keyStringPool;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    public long[] getOffsets() {
        return offsets;
    }

    public void setOffsets(long[] offsets) {
        this.offsets = offsets;
    }

    public StringPool getStringPool() {
        return stringPool;
    }

    public void setStringPool(StringPool stringPool) {
        this.stringPool = stringPool;
    }

    public int getDensity() {
        return density;
    }

    @Override
    public String toString() {
        return "Type{" +
                "name='" + name + '\'' +
                ", id=" + id +
                ", locale=" + locale +
                '}';
    }
}
