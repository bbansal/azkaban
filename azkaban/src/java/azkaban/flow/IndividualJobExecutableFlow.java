package azkaban.flow;

import azkaban.app.JavaJob;
import azkaban.app.LoggingJob;
import azkaban.common.jobs.DelegatingJob;
import azkaban.common.jobs.Job;
import azkaban.jobcontrol.impl.jobs.RetryingJob;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public class IndividualJobExecutableFlow implements ExecutableFlow
{

    private static final AtomicLong threadCounter = new AtomicLong(0);

    private final Object sync = new Object();
    private final String id;
    private final Job job;

    private volatile Status jobState;
    private volatile List<FlowCallback> callbacksToCall;
    private volatile DateTime startTime;
    private volatile DateTime endTime;

    public IndividualJobExecutableFlow(String id, Job job)
    {
        this.id = id;
        this.job = job;

        jobState = Status.READY;
        callbacksToCall = new ArrayList<FlowCallback>();
        startTime = null;
        endTime = null;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return job.getId();
    }

    @Override
    public void execute(FlowCallback callback)
    {
        synchronized (sync) {
            switch (jobState) {
                case READY:
                    jobState = Status.RUNNING;
                    startTime = new DateTime();
                    callbacksToCall.add(callback);
                    break;
                case RUNNING:
                    callbacksToCall.add(callback);
                    return;
                case COMPLETED:
                case SUCCEEDED:
                    callback.completed(Status.SUCCEEDED);
                    return;
                case FAILED:
                    callback.completed(Status.FAILED);
                    return;
            }
        }

        final ClassLoader storeMyClassLoader = Thread.currentThread().getContextClassLoader();

        Thread theThread = new Thread(
                new Runnable()
                {
                    @Override
                    public void run()
                    {
                        final List<FlowCallback> callbackList;

                        try {
                            job.run();
                        }
                        catch (RuntimeException e) {
                            synchronized (sync) {
                                jobState = Status.FAILED;
                                callbackList = callbacksToCall; // Get the reference before leaving the synchronized
                            }
                            callCallbacks(callbackList, jobState);

                            throw e;
                        }

                        synchronized (sync) {
                            jobState = Status.SUCCEEDED;
                            callbackList = callbacksToCall; // Get the reference before leaving the synchronized
                        }
                        callCallbacks(callbackList, jobState);
                    }

                    private void callCallbacks(final List<FlowCallback> callbackList, final Status status)
                    {
                        if (endTime == null) {
                            endTime = new DateTime();
                        }

                        Thread callbackThread = new Thread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                for (FlowCallback callback : callbackList) {
                                    try {
                                        callback.completed(status);
                                    }
                                    catch (RuntimeException t) {
                                        // TODO: Figure out how to use the logger to log that a callback threw an exception.
                                    }
                                }
                            }
                        },
                                                           String.format("%s-callback", Thread.currentThread().getName()));

                        // Use the primary Azkaban classloader for callbacks
                        // This is only needed for JavaJobs, but won't hurt other instances, so do for everything
                        callbackThread.setContextClassLoader(storeMyClassLoader);

                        callbackThread.start();
                    }
                },
                String.format("%s thread-%s", job.getId(), threadCounter.getAndIncrement())
        );

        Job currJob = job;
        while (true) {
            if (currJob instanceof JavaJob) {
                theThread.setContextClassLoader(((JavaJob) currJob).getDescriptor().getClassLoader());
                break;
            }
            else if (currJob instanceof DelegatingJob) {
                currJob = ((DelegatingJob) currJob).getInnerJob();
            }
            else {
                break;
            }
        }

        theThread.start();
    }

    @Override
    public boolean cancel()
    {
        final List<FlowCallback> callbacks;
        synchronized (sync) {
            switch(jobState) {
                case COMPLETED:
                case SUCCEEDED:
                case FAILED:
                    return true;
                default:
                    jobState = Status.FAILED;
                    callbacks = callbacksToCall;
                    callbacksToCall = new ArrayList<FlowCallback>();
            }
        }

        for (FlowCallback callback : callbacks) {
            callback.completed(Status.FAILED);
        }

        try {
            job.cancel();

            return true;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Status getStatus()
    {
        return jobState;
    }

    @Override
    public boolean reset()
    {
        synchronized (sync) {
            switch (jobState) {
                case RUNNING:
                    return false;
                default:
                    jobState = Status.READY;
                    callbacksToCall = new ArrayList<FlowCallback>();
                    startTime = null;
                    endTime = null;
            }
        }

        return true;
    }

    @Override
    public boolean markCompleted()
    {
        synchronized (sync) {
            switch (jobState) {
                case RUNNING:
                    return false;
                default:
                    jobState = Status.COMPLETED;
            }
        }
        return true;
    }

    @Override
    public boolean hasChildren()
    {
        return false;
    }

    @Override
    public List<ExecutableFlow> getChildren()
    {
        return Collections.emptyList();
    }

    @Override
    public String toString()
    {
        return "IndividualJobExecutableFlow{" +
               "job=" + job +
               ", jobState=" + jobState +
               '}';
    }

    @Override
    public DateTime getStartTime()
    {
        return startTime;
    }

    @Override
    public DateTime getEndTime()
    {
        return endTime;
    }

    Job getJob()
    {
        return job;
    }

    IndividualJobExecutableFlow setStatus(Status newStatus)
    {
        synchronized (sync) {
            switch (jobState) {
                case READY:
                    jobState = newStatus;
                    break;
                default:
                    throw new IllegalStateException("Can only set status when job is in the READY state.");
            }
        }

        return this;
    }

    IndividualJobExecutableFlow setStartTime(DateTime startTime)
    {
        this.startTime = startTime;

        return this;
    }

    IndividualJobExecutableFlow setEndTime(DateTime endTime)
    {
        this.endTime = endTime;

        return this;
    }
}
