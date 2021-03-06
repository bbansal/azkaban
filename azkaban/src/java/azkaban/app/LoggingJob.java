package azkaban.app;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import azkaban.common.jobs.DelegatingJob;
import azkaban.common.jobs.Job;
import azkaban.common.utils.Props;
import azkaban.common.utils.Utils;

/**
 * A wrapper for a job that attaches a Log4J appender to write to the logs
 * directory in the particular format expected
 * 
 * @author jkreps
 * 
 */
public class LoggingJob extends DelegatingJob {

    private static final Layout DEFAULT_LAYOUT = new PatternLayout("%d{dd-MM-yyyy HH:mm:ss} %c{1} %p - %m\n");

    private final Logger _logger;
    private final String _logDir;

    public LoggingJob(String logDir, Job innerJob, String loggerName) {
        super(innerJob);
        this._logDir = Utils.nonNull(logDir);
        this._logger = Logger.getLogger(loggerName);
    }

    @Override
    public void run() {
        String jobName = getInnerJob().getId();
        Utils.makePaths(new File(_logDir));
        File jobLogDir = new File(_logDir + File.separator + jobName);
        jobLogDir.mkdir();
        String date = DateTimeFormat.forPattern("MM-dd-yyyy.HH.mm.ss.SSS").print(new DateTime());
        File runLogDir = new File(jobLogDir, date);
        runLogDir.mkdir();
        String logName = new File(runLogDir, jobName + "." + date + ".log").getAbsolutePath();
        Appender jobAppender = null;
        try {
            jobAppender = new FileAppender(DEFAULT_LAYOUT, logName, false);
            _logger.addAppender(jobAppender);
        } catch(IOException e) {
            _logger.error("Could not open log file in " + _logDir, e);
        }

        boolean succeeded = false;
        boolean jobNotStaleException = false;
        long start = System.currentTimeMillis();
        try {
            getInnerJob().run();
            succeeded = true;
        } catch(Throwable t) {
            _logger.error("Fatal error occurred while running job '" + jobName + "':", t);
            throw new RuntimeException(t);
        } finally {
            long end = System.currentTimeMillis();
            Props props = new Props();
            props.put("start", Long.toString(start));
            props.put("end", Long.toString(end));
            props.put("succeeded", Boolean.toString(succeeded));
            props.put("jobNotStaleException", Boolean.toString(jobNotStaleException));
            try {
                props.storeLocal(new File(runLogDir, "run.properties"));
            } catch (IOException e) {
                _logger.warn(String.format("IOException when storing props to local dir[%s]", runLogDir), e);
                throw new RuntimeException(e);
            }

            if(jobAppender != null)
                _logger.removeAppender(jobAppender);
        }
    }

}
