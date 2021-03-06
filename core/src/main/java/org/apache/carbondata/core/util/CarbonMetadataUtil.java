/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.carbondata.core.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.carbondata.core.datastore.block.SegmentProperties;
import org.apache.carbondata.core.datastore.page.EncodedTablePage;
import org.apache.carbondata.core.datastore.page.statistics.TablePageStatistics;
import org.apache.carbondata.core.metadata.ColumnarFormatVersion;
import org.apache.carbondata.core.metadata.datatype.DataType;
import org.apache.carbondata.core.metadata.datatype.DataTypes;
import org.apache.carbondata.core.metadata.index.BlockIndexInfo;
import org.apache.carbondata.core.metadata.schema.table.column.CarbonMeasure;
import org.apache.carbondata.format.BlockIndex;
import org.apache.carbondata.format.BlockletBTreeIndex;
import org.apache.carbondata.format.BlockletIndex;
import org.apache.carbondata.format.BlockletInfo3;
import org.apache.carbondata.format.BlockletMinMaxIndex;
import org.apache.carbondata.format.ChunkCompressionMeta;
import org.apache.carbondata.format.ColumnSchema;
import org.apache.carbondata.format.CompressionCodec;
import org.apache.carbondata.format.DataChunk2;
import org.apache.carbondata.format.DataChunk3;
import org.apache.carbondata.format.Encoding;
import org.apache.carbondata.format.FileFooter3;
import org.apache.carbondata.format.FileHeader;
import org.apache.carbondata.format.IndexHeader;
import org.apache.carbondata.format.SegmentInfo;

/**
 * Util class to convert to thrift metdata classes
 */
public class CarbonMetadataUtil {

  /**
   * Below method prepares the file footer object for carbon data file version 3
   *
   * @param infoList
   * @param blockletIndexs
   * @param cardinalities
   * @param numberOfColumns
   * @return FileFooter
   */
  public static FileFooter3 convertFileFooterVersion3(List<BlockletInfo3> infoList,
      List<BlockletIndex> blockletIndexs, int[] cardinalities, int numberOfColumns)
      throws IOException {
    FileFooter3 footer = getFileFooter3(infoList, blockletIndexs, cardinalities, numberOfColumns);
    for (BlockletInfo3 info : infoList) {
      footer.addToBlocklet_info_list3(info);
    }
    return footer;
  }

  /**
   * Below method will be used to get the file footer object
   *
   * @param infoList         blocklet info
   * @param blockletIndexs
   * @param cardinalities    cardinlaity of dimension columns
   * @param numberOfColumns
   * @return file footer
   */
  private static FileFooter3 getFileFooter3(List<BlockletInfo3> infoList,
      List<BlockletIndex> blockletIndexs, int[] cardinalities, int numberOfColumns) {
    SegmentInfo segmentInfo = new SegmentInfo();
    segmentInfo.setNum_cols(numberOfColumns);
    segmentInfo.setColumn_cardinalities(CarbonUtil.convertToIntegerList(cardinalities));
    FileFooter3 footer = new FileFooter3();
    footer.setNum_rows(getNumberOfRowForFooter(infoList));
    footer.setSegment_info(segmentInfo);
    for (BlockletIndex info : blockletIndexs) {
      footer.addToBlocklet_index_list(info);
    }
    return footer;
  }

  public static BlockletIndex getBlockletIndex(
      org.apache.carbondata.core.metadata.blocklet.index.BlockletIndex info) {
    BlockletMinMaxIndex blockletMinMaxIndex = new BlockletMinMaxIndex();

    for (int i = 0; i < info.getMinMaxIndex().getMaxValues().length; i++) {
      blockletMinMaxIndex.addToMax_values(ByteBuffer.wrap(info.getMinMaxIndex().getMaxValues()[i]));
      blockletMinMaxIndex.addToMin_values(ByteBuffer.wrap(info.getMinMaxIndex().getMinValues()[i]));
    }
    BlockletBTreeIndex blockletBTreeIndex = new BlockletBTreeIndex();
    blockletBTreeIndex.setStart_key(info.getBtreeIndex().getStartKey());
    blockletBTreeIndex.setEnd_key(info.getBtreeIndex().getEndKey());
    BlockletIndex blockletIndex = new BlockletIndex();
    blockletIndex.setMin_max_index(blockletMinMaxIndex);
    blockletIndex.setB_tree_index(blockletBTreeIndex);
    return blockletIndex;
  }

  /**
   * Get total number of rows for the file.
   *
   * @param infoList
   * @return
   */
  private static long getNumberOfRowForFooter(List<BlockletInfo3> infoList) {
    long numberOfRows = 0;
    for (BlockletInfo3 info : infoList) {
      numberOfRows += info.num_rows;
    }
    return numberOfRows;
  }

  public static BlockletIndex getBlockletIndex(List<EncodedTablePage> encodedTablePageList,
      List<CarbonMeasure> carbonMeasureList) {
    BlockletMinMaxIndex blockletMinMaxIndex = new BlockletMinMaxIndex();
    // Calculating min/max for every each column.
    TablePageStatistics stats = new TablePageStatistics(encodedTablePageList.get(0).getDimensions(),
        encodedTablePageList.get(0).getMeasures());
    byte[][] minCol = stats.getDimensionMinValue().clone();
    byte[][] maxCol = stats.getDimensionMaxValue().clone();

    for (EncodedTablePage encodedTablePage : encodedTablePageList) {
      stats = new TablePageStatistics(encodedTablePage.getDimensions(),
          encodedTablePage.getMeasures());
      byte[][] columnMaxData = stats.getDimensionMaxValue();
      byte[][] columnMinData = stats.getDimensionMinValue();
      for (int i = 0; i < maxCol.length; i++) {
        if (ByteUtil.UnsafeComparer.INSTANCE.compareTo(columnMaxData[i], maxCol[i]) > 0) {
          maxCol[i] = columnMaxData[i];
        }
        if (ByteUtil.UnsafeComparer.INSTANCE.compareTo(columnMinData[i], minCol[i]) < 0) {
          minCol[i] = columnMinData[i];
        }
      }
    }
    // Writing min/max to thrift file
    for (byte[] max : maxCol) {
      blockletMinMaxIndex.addToMax_values(ByteBuffer.wrap(max));
    }
    for (byte[] min : minCol) {
      blockletMinMaxIndex.addToMin_values(ByteBuffer.wrap(min));
    }

    stats = new TablePageStatistics(encodedTablePageList.get(0).getDimensions(),
        encodedTablePageList.get(0).getMeasures());
    byte[][] measureMaxValue = stats.getMeasureMaxValue().clone();
    byte[][] measureMinValue = stats.getMeasureMinValue().clone();
    byte[] minVal = null;
    byte[] maxVal = null;
    for (int i = 1; i < encodedTablePageList.size(); i++) {
      for (int j = 0; j < measureMinValue.length; j++) {
        stats = new TablePageStatistics(
            encodedTablePageList.get(i).getDimensions(), encodedTablePageList.get(i).getMeasures());
        minVal = stats.getMeasureMinValue()[j];
        maxVal = stats.getMeasureMaxValue()[j];
        if (compareMeasureData(measureMaxValue[j], maxVal, carbonMeasureList.get(j).getDataType())
            < 0) {
          measureMaxValue[j] = maxVal.clone();
        }
        if (compareMeasureData(measureMinValue[j], minVal, carbonMeasureList.get(j).getDataType())
            > 0) {
          measureMinValue[j] = minVal.clone();
        }
      }
    }

    for (byte[] max : measureMaxValue) {
      blockletMinMaxIndex.addToMax_values(ByteBuffer.wrap(max));
    }
    for (byte[] min : measureMinValue) {
      blockletMinMaxIndex.addToMin_values(ByteBuffer.wrap(min));
    }
    BlockletBTreeIndex blockletBTreeIndex = new BlockletBTreeIndex();
    byte[] startKey = encodedTablePageList.get(0).getPageKey().serializeStartKey();
    blockletBTreeIndex.setStart_key(startKey);
    byte[] endKey = encodedTablePageList.get(
        encodedTablePageList.size() - 1).getPageKey().serializeEndKey();
    blockletBTreeIndex.setEnd_key(endKey);
    BlockletIndex blockletIndex = new BlockletIndex();
    blockletIndex.setMin_max_index(blockletMinMaxIndex);
    blockletIndex.setB_tree_index(blockletBTreeIndex);
    return blockletIndex;
  }

  /**
   * @param blockIndex
   * @param encoding
   * @param columnSchemas
   * @param segmentProperties
   * @return return true if given encoding is present in column
   */
  private static boolean containsEncoding(int blockIndex, Encoding encoding,
      List<ColumnSchema> columnSchemas, SegmentProperties segmentProperties) {
    Set<Integer> dimOrdinals = segmentProperties.getDimensionOrdinalForBlock(blockIndex);
    // column groups will always have dictionary encoding
    if (dimOrdinals.size() > 1 && Encoding.DICTIONARY == encoding) {
      return true;
    }
    for (Integer dimOrdinal : dimOrdinals) {
      if (columnSchemas.get(dimOrdinal).encoders.contains(encoding)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Right now it is set to default values. We may use this in future
   */
  public static ChunkCompressionMeta getSnappyChunkCompressionMeta() {
    ChunkCompressionMeta chunkCompressionMeta = new ChunkCompressionMeta();
    chunkCompressionMeta.setCompression_codec(CompressionCodec.SNAPPY);
    chunkCompressionMeta.setTotal_compressed_size(0);
    chunkCompressionMeta.setTotal_uncompressed_size(0);
    return chunkCompressionMeta;
  }

  /**
   * Below method will be used to get the index header
   *
   * @param columnCardinality cardinality of each column
   * @param columnSchemaList  list of column present in the table
   * @return Index header object
   */
  public static IndexHeader getIndexHeader(int[] columnCardinality,
      List<ColumnSchema> columnSchemaList, int bucketNumber) {
    // create segment info object
    SegmentInfo segmentInfo = new SegmentInfo();
    // set the number of columns
    segmentInfo.setNum_cols(columnSchemaList.size());
    // setting the column cardinality
    segmentInfo.setColumn_cardinalities(CarbonUtil.convertToIntegerList(columnCardinality));
    // create index header object
    IndexHeader indexHeader = new IndexHeader();
    ColumnarFormatVersion version = CarbonProperties.getInstance().getFormatVersion();
    indexHeader.setVersion(version.number());
    // set the segment info
    indexHeader.setSegment_info(segmentInfo);
    // set the column names
    indexHeader.setTable_columns(columnSchemaList);
    // set the bucket number
    indexHeader.setBucket_id(bucketNumber);
    return indexHeader;
  }

  /**
   * Below method will be used to get the block index info thrift object for
   * each block present in the segment
   *
   * @param blockIndexInfoList block index info list
   * @return list of block index
   */
  public static List<BlockIndex> getBlockIndexInfo(List<BlockIndexInfo> blockIndexInfoList) {
    List<BlockIndex> thriftBlockIndexList = new ArrayList<BlockIndex>();
    BlockIndex blockIndex = null;
    // below code to create block index info object for each block
    for (BlockIndexInfo blockIndexInfo : blockIndexInfoList) {
      blockIndex = new BlockIndex();
      blockIndex.setNum_rows(blockIndexInfo.getNumberOfRows());
      blockIndex.setOffset(blockIndexInfo.getOffset());
      blockIndex.setFile_name(blockIndexInfo.getFileName());
      blockIndex.setBlock_index(getBlockletIndex(blockIndexInfo.getBlockletIndex()));
      if (blockIndexInfo.getBlockletInfo() != null) {
        blockIndex.setBlocklet_info(getBlocletInfo3(blockIndexInfo.getBlockletInfo()));
      }
      thriftBlockIndexList.add(blockIndex);
    }
    return thriftBlockIndexList;
  }

  public static BlockletInfo3 getBlocletInfo3(
      org.apache.carbondata.core.metadata.blocklet.BlockletInfo blockletInfo) {
    List<Long> dimensionChunkOffsets = blockletInfo.getDimensionChunkOffsets();
    dimensionChunkOffsets.addAll(blockletInfo.getMeasureChunkOffsets());
    List<Integer> dimensionChunksLength = blockletInfo.getDimensionChunksLength();
    dimensionChunksLength.addAll(blockletInfo.getMeasureChunksLength());
    return new BlockletInfo3(blockletInfo.getNumberOfRows(), dimensionChunkOffsets,
        dimensionChunksLength, blockletInfo.getDimensionOffset(), blockletInfo.getMeasureOffsets(),
        blockletInfo.getNumberOfPages());
  }

  /**
   * return DataChunk3 that contains the input DataChunk2 list
   */
  public static DataChunk3 getDataChunk3(List<DataChunk2> dataChunksList) {
    int offset = 0;
    DataChunk3 dataChunk = new DataChunk3();
    List<Integer> pageOffsets = new ArrayList<>();
    List<Integer> pageLengths = new ArrayList<>();
    int length = 0;
    for (DataChunk2 dataChunk2 : dataChunksList) {
      pageOffsets.add(offset);
      length = dataChunk2.getData_page_length() + dataChunk2.getRle_page_length() +
          dataChunk2.getRowid_page_length();
      pageLengths.add(length);
      offset += length;
    }
    dataChunk.setData_chunk_list(dataChunksList);
    dataChunk.setPage_length(pageLengths);
    dataChunk.setPage_offset(pageOffsets);
    return dataChunk;
  }

  /**
   * return DataChunk3 for the dimension column (specifed by `columnIndex`)
   * in `encodedTablePageList`
   */
  public static DataChunk3 getDimensionDataChunk3(List<EncodedTablePage> encodedTablePageList,
      int columnIndex) throws IOException {
    List<DataChunk2> dataChunksList = new ArrayList<>(encodedTablePageList.size());
    for (EncodedTablePage encodedTablePage : encodedTablePageList) {
      dataChunksList.add(encodedTablePage.getDimension(columnIndex).getPageMetadata());
    }
    return CarbonMetadataUtil.getDataChunk3(dataChunksList);
  }

  /**
   * return DataChunk3 for the measure column (specifed by `columnIndex`)
   * in `encodedTablePageList`
   */
  public static DataChunk3 getMeasureDataChunk3(List<EncodedTablePage> encodedTablePageList,
      int columnIndex) throws IOException {
    List<DataChunk2> dataChunksList = new ArrayList<>(encodedTablePageList.size());
    for (EncodedTablePage encodedTablePage : encodedTablePageList) {
      dataChunksList.add(encodedTablePage.getMeasure(columnIndex).getPageMetadata());
    }
    return CarbonMetadataUtil.getDataChunk3(dataChunksList);
  }

  private static int compareMeasureData(byte[] first, byte[] second, DataType dataType) {
    ByteBuffer firstBuffer = null;
    ByteBuffer secondBuffer = null;
    if (dataType == DataTypes.BOOLEAN) {
      return first[0] - second[0];
    } else if (dataType == DataTypes.DOUBLE) {
      firstBuffer = ByteBuffer.allocate(8);
      firstBuffer.put(first);
      secondBuffer = ByteBuffer.allocate(8);
      secondBuffer.put(second);
      firstBuffer.flip();
      secondBuffer.flip();
      return (int) (firstBuffer.getDouble() - secondBuffer.getDouble());
    } else if (dataType == DataTypes.LONG || dataType == DataTypes.INT
        || dataType == DataTypes.SHORT) {
      firstBuffer = ByteBuffer.allocate(8);
      firstBuffer.put(first);
      secondBuffer = ByteBuffer.allocate(8);
      secondBuffer.put(second);
      firstBuffer.flip();
      secondBuffer.flip();
      return (int) (firstBuffer.getLong() - secondBuffer.getLong());
    } else if (DataTypes.isDecimal(dataType)) {
      return DataTypeUtil.byteToBigDecimal(first).compareTo(DataTypeUtil.byteToBigDecimal(second));
    } else {
      throw new IllegalArgumentException("Invalid data type");
    }
  }

  /**
   * Below method will be used to prepare the file header object for carbondata file
   *
   * @param isFooterPresent  is footer present in carbon data file
   * @param columnSchemaList list of column schema
   * @param schemaUpdatedTimeStamp  schema updated time stamp to be used for restructure scenarios
   * @return file header thrift object
   */
  public static FileHeader getFileHeader(boolean isFooterPresent,
      List<ColumnSchema> columnSchemaList, long schemaUpdatedTimeStamp) {
    FileHeader fileHeader = new FileHeader();
    ColumnarFormatVersion version = CarbonProperties.getInstance().getFormatVersion();
    fileHeader.setIs_footer_present(isFooterPresent);
    fileHeader.setColumn_schema(columnSchemaList);
    fileHeader.setVersion(version.number());
    fileHeader.setTime_stamp(schemaUpdatedTimeStamp);
    return fileHeader;
  }

}
