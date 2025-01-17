/*
 * Copyright 2023 Ververica Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cdc.common.serializer.schema;

import org.apache.flink.api.common.typeutils.SimpleTypeSerializerSnapshot;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.api.common.typeutils.TypeSerializerSnapshot;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

import com.ververica.cdc.common.schema.Column;
import com.ververica.cdc.common.schema.MetadataColumn;
import com.ververica.cdc.common.schema.PhysicalColumn;
import com.ververica.cdc.common.serializer.EnumSerializer;
import com.ververica.cdc.common.serializer.TypeSerializerSingleton;
import com.ververica.cdc.common.types.DataTypes;

import java.io.IOException;

import static com.ververica.cdc.common.serializer.schema.ColumnSerializer.ColumnType.METADATA;
import static com.ververica.cdc.common.serializer.schema.ColumnSerializer.ColumnType.PHYSICAL;

/** A {@link TypeSerializer} for {@link Column}. */
public class ColumnSerializer extends TypeSerializerSingleton<Column> {

    private static final long serialVersionUID = 1L;

    /** Sharable instance of the TableIdSerializer. */
    public static final ColumnSerializer INSTANCE = new ColumnSerializer();

    private final EnumSerializer<ColumnType> enumSerializer =
            new EnumSerializer<>(ColumnType.class);
    private final PhysicalColumnSerializer physicalColumnSerializer =
            PhysicalColumnSerializer.INSTANCE;
    private final MetadataColumnSerializer metadataColumnSerializer =
            MetadataColumnSerializer.INSTANCE;

    @Override
    public boolean isImmutableType() {
        return false;
    }

    @Override
    public Column createInstance() {
        return Column.physicalColumn("unknown", DataTypes.BIGINT());
    }

    @Override
    public Column copy(Column from) {
        if (from instanceof PhysicalColumn) {
            return physicalColumnSerializer.copy((PhysicalColumn) from);
        } else if (from instanceof MetadataColumn) {
            return metadataColumnSerializer.copy((MetadataColumn) from);
        } else {
            throw new IllegalArgumentException("Unknown column type: " + from);
        }
    }

    @Override
    public Column copy(Column from, Column reuse) {
        return copy(from);
    }

    @Override
    public int getLength() {
        return -1;
    }

    @Override
    public void serialize(Column record, DataOutputView target) throws IOException {
        if (record instanceof PhysicalColumn) {
            enumSerializer.serialize(PHYSICAL, target);
            physicalColumnSerializer.serialize((PhysicalColumn) record, target);
        } else if (record instanceof MetadataColumn) {
            enumSerializer.serialize(METADATA, target);
            metadataColumnSerializer.serialize((MetadataColumn) record, target);
        } else {
            throw new IOException("Unknown column type: " + record);
        }
    }

    @Override
    public Column deserialize(DataInputView source) throws IOException {
        ColumnType columnType = enumSerializer.deserialize(source);
        switch (columnType) {
            case METADATA:
                return metadataColumnSerializer.deserialize(source);
            case PHYSICAL:
                return physicalColumnSerializer.deserialize(source);
            default:
                throw new IOException("Unknown column type: " + columnType);
        }
    }

    @Override
    public Column deserialize(Column reuse, DataInputView source) throws IOException {
        return deserialize(source);
    }

    @Override
    public void copy(DataInputView source, DataOutputView target) throws IOException {
        serialize(deserialize(source), target);
    }

    @Override
    public TypeSerializerSnapshot<Column> snapshotConfiguration() {
        return new ColumnSerializerSnapshot();
    }

    /** Serializer configuration snapshot for compatibility and format evolution. */
    @SuppressWarnings("WeakerAccess")
    public static final class ColumnSerializerSnapshot
            extends SimpleTypeSerializerSnapshot<Column> {

        public ColumnSerializerSnapshot() {
            super(ColumnSerializer::new);
        }
    }

    enum ColumnType {
        PHYSICAL,
        METADATA
    }
}
