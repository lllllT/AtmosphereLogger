package org.tamanegi.atmosphere;

import java.io.File;
import java.io.RandomAccessFile;

import android.content.Context;

public class LogData
{
    public static final int TOTAL_COUNT = 12 * 24 * 32;

    private static final int HEADER_SIZE = 4 * 3;
    private static final int RECORD_SIZE = 8 + 4;
    private static final String VALUES_FILENAME = "values.dat";

    private static LogData instance = null;

    private File file;

    public static class LogRecord
    {
        public long time;
        public float value;

        public LogRecord()
        {
            this.time = 0;
            this.value = 0;
        }

        public LogRecord(long time, float value)
        {
            this.time = time;
            this.value = value;
        }
    }

    public static LogData getInstance(Context context)
    {
        synchronized(LogData.class) {
            if(instance == null) {
                instance = new LogData(context.getApplicationContext());
            }
        }

        return instance;
    }

    private LogData(Context context)
    {
        file = context.getFileStreamPath(VALUES_FILENAME);
    }

    public synchronized void writeRecord(LogRecord record)
    {
        RandomAccessFile out = null;
        try {
            boolean exist = file.isFile();
            out = new RandomAccessFile(file, "rw");

            int total_cnt, avail_cnt, first_idx;

            out.seek(0);
            if(exist) {
                // read header
                total_cnt = out.readInt();
                avail_cnt = out.readInt();
                first_idx = out.readInt();
            }
            else {
                // initial header
                total_cnt = TOTAL_COUNT;
                avail_cnt = 0;
                first_idx = 0;
            }

            int next_idx = (first_idx + avail_cnt) % total_cnt;
            if(avail_cnt < total_cnt) {
                avail_cnt = avail_cnt + 1;
            }
            else {
                first_idx = (first_idx + 1) % total_cnt;
            }

            // write record
            out.seek(HEADER_SIZE + RECORD_SIZE * next_idx);
            out.writeLong(record.time);
            out.writeFloat(record.value);

            // update header
            out.seek(0);
            out.writeInt(total_cnt);
            out.writeInt(avail_cnt);
            out.writeInt(first_idx);
        }
        catch(Exception e) {
            // ignore
        }
        finally {
            if(out != null) {
                try {
                    out.close();
                }
                catch(Exception e) {
                    // ignore
                }
            }
        }
    }

    public synchronized int readRecords(LogRecord[] records)
    {
        RandomAccessFile in = null;
        try {
            boolean exist = file.isFile();
            if(! exist) {
                return 0;
            }

            in = new RandomAccessFile(file, "r");

            // read header
            in.seek(0);
            int total_cnt = in.readInt();
            int avail_cnt = in.readInt();
            int first_idx = in.readInt();

            for(int i = 0; i < avail_cnt; i++) {
                if(records[i] == null) {
                    records[i] = new LogRecord();
                }

                int idx = (first_idx + i) % total_cnt;
                in.seek(HEADER_SIZE + RECORD_SIZE * idx);
                records[i].time = in.readLong();
                records[i].value = in.readFloat();
            }

            return avail_cnt;
        }
        catch(Exception e) {
            // ignore
            return 0;
        }
        finally {
            if(in != null) {
                try {
                    in.close();
                }
                catch(Exception e) {
                    // ignore
                }
            }
        }
    }

    public synchronized long getLastTimestamp()
    {
        RandomAccessFile in = null;
        try {
            boolean exist = file.isFile();
            if(! exist) {
                return -1;
            }

            in = new RandomAccessFile(file, "r");

            // read header
            in.seek(0);
            int total_cnt = in.readInt();
            int avail_cnt = in.readInt();
            int first_idx = in.readInt();
            if(avail_cnt <= 0) {
                return -1;
            }

            int idx = (first_idx + avail_cnt) % total_cnt;

            in.seek(HEADER_SIZE + RECORD_SIZE * idx);
            long time = in.readLong();

            return time;
        }
        catch(Exception e) {
            // ignore
            return -1;
        }
        finally {
            if(in != null) {
                try {
                    in.close();
                }
                catch(Exception e) {
                    // ignore
                }
            }
        }
    }
}
