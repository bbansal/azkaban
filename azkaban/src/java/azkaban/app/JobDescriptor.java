package azkaban.app;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import azkaban.common.jobs.Job;
import azkaban.common.utils.Props;
import azkaban.common.utils.Utils;

/**
 * A job descriptor represents the configuration information for a job This
 * includes the job class, the job properties, the job dependencies, and the
 * job's id.
 * 
 * This serves as a template for creating Job instances when the time comes to
 * run the Job.
 * 
 * @author jkreps
 * 
 */
public class JobDescriptor {

    public static final String JOB_TYPE = "type";
    public static final String JOB_CLASS = "job.class";
    public static final String READ_LOCKS = "read.lock";
    public static final String WRITE_LOCKS = "write.lock";
    public static final String RETRIES = "retries";
    public static final String RETRY_BACKOFF = "retry.backoff";
    public static final String JOB_PERMITS = "job.permits";
    public static final String NOTIFY_EMAIL = "notify.emails";

    public static final Comparator<JobDescriptor> NAME_COMPARATOR = new Comparator<JobDescriptor>() {

        public int compare(JobDescriptor d1, JobDescriptor d2) {
            return d1.getId().compareTo(d2.getId());
        }
    };

    private final String _id;
    private final String _path;
    private Class<?> _class;
    private final int _retries;
    private final long _retryBackoffMs;
    private final Integer _requiredPermits;
    private final Props _props;
    private final Set<JobDescriptor> _dependencies;
    private final ClassLoader _classLoader;
    private final List<String> _readResourceLocks;
    private final List<String> _writeResourceLocks;
    private final List<String> _emailList;
    private final String _jobType;

    public JobDescriptor(String id, String path, Props props, ClassLoader classLoader) {
        this._id = id;
        this._path = path;
        this._props = props;

        this._jobType = props.getString(JOB_TYPE, "");

        // @TODO Move this validation check in Java Job
        if(_jobType.length() == 0 || _jobType.equalsIgnoreCase("java")) {
            String className = props.getString(JOB_CLASS);
            this._class = Utils.loadClass(className, classLoader);
        }

        this._readResourceLocks = props.getStringList(READ_LOCKS, ",");

        this._dependencies = new HashSet<JobDescriptor>();
        this._retries = props.getInt(RETRIES, 0);
        this._retryBackoffMs = props.getLong(RETRY_BACKOFF, 0);
        this._requiredPermits = props.getInt(JOB_PERMITS, 0);
        this._classLoader = classLoader;

        this._writeResourceLocks = props.getStringList(WRITE_LOCKS, ",");

        // Ordered resource locking should help prevent simple deadlocking
        // situations.
        Collections.sort(this._readResourceLocks);
        Collections.sort(this._writeResourceLocks);

        this._emailList = props.getStringList(NOTIFY_EMAIL);
    }

    /**
     * Add a dependency to this job
     * 
     * @param dep
     */
    public void addDependency(JobDescriptor dep) {
        this._dependencies.add(dep);
    }

    public String getId() {
        return this._id;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Job> getJobClass() {
        return (Class<? extends Job>) this._class;
    }

    public Set<JobDescriptor> getDependencies() {
        return this._dependencies;
    }

    public Props getProps() {
        return this._props;
    }

    public boolean hasDependencies() {
        return this._dependencies.size() > 0;
    }

    public int getRetries() {
        return this._retries;
    }

    public long getRetryBackoffMs() {
        return this._retryBackoffMs;
    }

    public String getPath() {
        return this._path;
    }

    @Override
    public String toString() {
        return String.format(
                "Job(id=%s, class=%s, path=%s, deps = %s)",
                _id,
                (_class == null? "?" : _class.getName()),
                _path,
                getProps().getStringList("dependencies", null, "\\s*,\\s*")
        );
    }

    public ClassLoader getClassLoader() {
        return this._classLoader;
    }

    public int getNumRequiredPermits() {
        return this._requiredPermits;
    }

    public List<String> getReadResourceLocks() {
        return _readResourceLocks;
    }

    public List<String> getWriteResourceLocks() {
        return _writeResourceLocks;
    }

    public List<String> getEmailNotificationList() {
        return _emailList;
    }

    public String getJobType() {
        return _jobType;
    }
}