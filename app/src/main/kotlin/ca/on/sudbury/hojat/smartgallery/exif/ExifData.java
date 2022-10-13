package ca.on.sudbury.hojat.smartgallery.exif;

import java.util.List;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import timber.log.Timber;

import java.util.Collections;

/**
 * This class stores the EXIF header in IFDs according to the JPEG
 * specification. It is the result produced by {@link ExifReader}.
 *
 * @see ExifReader
 * @see IfdData
 */
class ExifData {
    private static final byte[] USER_COMMENT_ASCII = {0x41, 0x53, 0x43, 0x49, 0x49, 0x00, 0x00, 0x00};
    private static final byte[] USER_COMMENT_JIS = {0x4A, 0x49, 0x53, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] USER_COMMENT_UNICODE = {0x55, 0x4E, 0x49, 0x43, 0x4F, 0x44, 0x45, 0x00};

    private List<ExifParser.Section> mSections;
    private final IfdData[] mIfdDatas = new IfdData[IfdId.TYPE_IFD_COUNT];
    private final ByteOrder mByteOrder;
    private byte[] mThumbnail;
    private final ArrayList<byte[]> mStripBytes = new ArrayList<>();
    private int qualityGuess = 0;
    private int imageLength = -1, imageWidth = -1;
    private short jpegProcess = 0;
    public int mUncompressedDataPosition = 0;

    ExifData(ByteOrder order) {
        mByteOrder = order;
    }

    /**
     * Gets the compressed thumbnail. Returns null if there is no compressed
     * thumbnail.
     *
     * @see #hasCompressedThumbnail()
     */
    protected byte[] getCompressedThumbnail() {
        return mThumbnail;
    }

    /**
     * Sets the compressed thumbnail.
     */
    protected void setCompressedThumbnail(byte[] thumbnail) {
        mThumbnail = thumbnail;
    }

    /**
     * Returns true it this header contains a compressed thumbnail.
     */
    protected boolean hasCompressedThumbnail() {
        return mThumbnail != null;
    }

    /**
     * Adds an uncompressed strip.
     */
    protected void setStripBytes(int index, byte[] strip) {
        if (index < mStripBytes.size()) {
            mStripBytes.set(index, strip);
        } else {
            for (int i = mStripBytes.size(); i < index; i++) {
                mStripBytes.add(null);
            }
            mStripBytes.add(strip);
        }
    }

    /**
     * Gets the strip count.
     */
    protected int getStripCount() {
        return mStripBytes.size();
    }

    /**
     * Gets the strip at the specified index.
     *
     * @exceptions #IndexOutOfBoundException
     */
    protected byte[] getStrip(int index) {
        return mStripBytes.get(index);
    }

    /**
     * Returns true if this header contains uncompressed strip.
     */
    protected boolean hasUncompressedStrip() {
        return mStripBytes.size() != 0;
    }

    /**
     * Gets the byte order.
     */
    protected ByteOrder getByteOrder() {
        return mByteOrder;
    }

    /**
     * Adds IFD data. If IFD data of the same type already exists, it will be
     * replaced by the new data.
     */
    protected void addIfdData(IfdData data) {
        mIfdDatas[data.getId()] = data;
    }

    /**
     * Returns the tag with a given TID in the given IFD if the tag exists.
     * Otherwise returns null.
     */
    protected ExifTag getTag(short tag, int ifd) {
        IfdData ifdData = mIfdDatas[ifd];
        return (ifdData == null) ? null : ifdData.getTag(tag);
    }

    /**
     * Adds the given ExifTag to its default IFD and returns an existing ExifTag
     * with the same TID or null if none exist.
     */
    protected ExifTag addTag(ExifTag tag) {
        if (tag != null) {
            int ifd = tag.getIfd();
            return addTag(tag, ifd);
        }
        return null;
    }

    /**
     * Adds the given ExifTag to the given IFD and returns an existing ExifTag
     * with the same TID or null if none exist.
     */
    protected ExifTag addTag(ExifTag tag, int ifdId) {
        if (tag != null && ExifTag.isValidIfd(ifdId)) {
            IfdData ifdData = getOrCreateIfdData(ifdId);
            return ifdData.setTag(tag);
        }
        return null;
    }

    /**
     * Returns the {@link IfdData} object corresponding to a given IFD or
     * generates one if none exist.
     */
    protected IfdData getOrCreateIfdData(int ifdId) {
        IfdData ifdData = mIfdDatas[ifdId];
        if (ifdData == null) {
            ifdData = new IfdData(ifdId);
            mIfdDatas[ifdId] = ifdData;
        }
        return ifdData;
    }

    /**
     * Removes the thumbnail and its related tags. IFD1 will be removed.
     */
    protected void removeThumbnailData() {
        clearThumbnailAndStrips();
        mIfdDatas[IfdId.TYPE_IFD_1] = null;
    }

    protected void clearThumbnailAndStrips() {
        mThumbnail = null;
        mStripBytes.clear();
    }

    /**
     * Removes the tag with a given TID and IFD.
     */
    protected void removeTag(short tagId, int ifdId) {
        IfdData ifdData = mIfdDatas[ifdId];
        if (ifdData == null) {
            return;
        }
        ifdData.removeTag(tagId);
    }

    /**
     * Decodes the user comment tag into string as specified in the EXIF
     * standard. Returns null if decoding failed.
     */
    protected String getUserComment() {
        IfdData ifdData = mIfdDatas[IfdId.TYPE_IFD_0];
        if (ifdData == null) {
            return null;
        }
        ExifTag tag = ifdData.getTag(ExifInterface.getTrueTagKey(ExifInterface.TAG_USER_COMMENT));
        if (tag == null) {
            return null;
        }
        if (tag.getComponentCount() < 8) {
            return null;
        }

        byte[] buf = new byte[tag.getComponentCount()];
        tag.getBytes(buf);

        byte[] code = new byte[8];
        System.arraycopy(buf, 0, code, 0, 8);

        try {
            if (Arrays.equals(code, USER_COMMENT_ASCII)) {
                return new String(buf, 8, buf.length - 8, StandardCharsets.US_ASCII);
            } else if (Arrays.equals(code, USER_COMMENT_JIS)) {
                return new String(buf, 8, buf.length - 8, "EUC-JP");
            } else if (Arrays.equals(code, USER_COMMENT_UNICODE)) {
                return new String(buf, 8, buf.length - 8, StandardCharsets.UTF_16);
            } else {
                return null;
            }
        } catch (UnsupportedEncodingException e) {
            Timber.w("Failed to decode the user comment");
            return null;
        }
    }

    /**
     * Returns a list of all {@link ExifTag}s in the ExifData or null if there
     * are none.
     */
    protected List<ExifTag> getAllTags() {
        ArrayList<ExifTag> ret = new ArrayList<>();
        for (IfdData d : mIfdDatas) {
            if (d != null) {
                ExifTag[] tags = d.getAllTags();
                if (tags != null) {
                    Collections.addAll(ret, tags);
                }
            }
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link ExifTag}s in a given IFD or null if there
     * are none.
     */
    protected java.util.List<ExifTag> getAllTagsForIfd(int ifd) {
        IfdData d = mIfdDatas[ifd];
        if (d == null) {
            return null;
        }
        ExifTag[] tags = d.getAllTags();
        if (tags == null) {
            return null;
        }
        ArrayList<ExifTag> ret = new ArrayList<>(tags.length);
        Collections.addAll(ret, tags);
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    /**
     * Returns a list of all {@link ExifTag}s with a given TID or null if there
     * are none.
     */
    protected List<ExifTag> getAllTagsForTagId(short tag) {
        ArrayList<ExifTag> ret = new ArrayList<>();
        for (IfdData d : mIfdDatas) {
            if (d != null) {
                ExifTag t = d.getTag(tag);
                if (t != null) {
                    ret.add(t);
                }
            }
        }
        if (ret.size() == 0) {
            return null;
        }
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof ExifData) {
            ExifData data = (ExifData) obj;
            if (data.mByteOrder != mByteOrder ||
                    data.mStripBytes.size() != mStripBytes.size() ||
                    !Arrays.equals(data.mThumbnail, mThumbnail)) {
                return false;
            }
            for (int i = 0; i < mStripBytes.size(); i++) {
                if (!Arrays.equals(data.mStripBytes.get(i), mStripBytes.get(i))) {
                    return false;
                }
            }
            for (int i = 0; i < IfdId.TYPE_IFD_COUNT; i++) {
                IfdData ifd1 = data.getIfdData(i);
                IfdData ifd2 = getIfdData(i);
                if (ifd1 != ifd2 && ifd1 != null && !ifd1.equals(ifd2)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the {@link IfdData} object corresponding to a given IFD if it
     * exists or null.
     */
    protected IfdData getIfdData(int ifdId) {
        if (ExifTag.isValidIfd(ifdId)) {
            return mIfdDatas[ifdId];
        }
        return null;
    }

    protected void setQualityGuess(final int qualityGuess) {
        this.qualityGuess = qualityGuess;
    }

    public int getQualityGuess() {
        return qualityGuess;
    }

    protected void setImageSize(final int imageWidth, final int imageLength) {
        this.imageWidth = imageWidth;
        this.imageLength = imageLength;
    }

    public int[] getImageSize() {
        return new int[]{imageWidth, imageLength};
    }

    public void setJpegProcess(final short jpegProcess) {
        this.jpegProcess = jpegProcess;
    }

    public short getJpegProcess() {
        return this.jpegProcess;
    }

    public void setSections(final List<ExifParser.Section> sections) {
        mSections = sections;
    }

    public List<ExifParser.Section> getSections() {
        return mSections;
    }
}
