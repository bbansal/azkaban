package azkaban.flow;

import org.easymock.Capture;
import org.easymock.IAnswer;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class ComposedExecutableFlowTest
{
    private volatile ExecutableFlow dependerFlow;
    private volatile ExecutableFlow dependeeFlow;
    private ComposedExecutableFlow flow;

    @Before
    public void setUp() throws Exception
    {
        dependerFlow = EasyMock.createMock(ExecutableFlow.class);
        dependeeFlow = EasyMock.createMock(ExecutableFlow.class);

        EasyMock.expect(dependerFlow.getStatus()).andReturn(Status.READY).once();
        EasyMock.replay(dependerFlow);

        flow = new ComposedExecutableFlow("blah", dependerFlow, dependeeFlow);

        EasyMock.verify(dependerFlow);
        EasyMock.reset(dependerFlow);
    }

    @After
    public void tearDown() throws Exception
    {
        EasyMock.verify(dependerFlow);
        EasyMock.verify(dependeeFlow);
    }

    @Test
    public void testSanity() throws Exception
    {
        final AtomicBoolean dependeeRan = new AtomicBoolean(false);

        final Capture<FlowCallback> dependeeCallback = new Capture<FlowCallback>();
        dependeeFlow.execute(EasyMock.capture(dependeeCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee already ran!?", dependeeRan.compareAndSet(false, true));
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependeeCallback.getValue().completed(Status.SUCCEEDED);

                return null;
            }
        }).once();

        final Capture<FlowCallback> dependerCallback = new Capture<FlowCallback>();
        dependerFlow.execute(EasyMock.capture(dependerCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee must run before depender", dependeeRan.get());
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependerCallback.getValue().completed(Status.SUCCEEDED);

                return null;
            }
        }).once();

        EasyMock.replay(dependerFlow, dependeeFlow);

        Assert.assertEquals(Status.READY, flow.getStatus());

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.SUCCEEDED, status);
            }
        });

        Assert.assertTrue("Internal flow executes never ran.", dependeeRan.get());
        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());

        callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.SUCCEEDED, status);
            }
        });

        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());
    }

    @Test
    public void testFailureInDependee() throws Exception
    {
        final AtomicBoolean dependeeRan = new AtomicBoolean(false);

        final Capture<FlowCallback> dependeeCallback = new Capture<FlowCallback>();
        dependeeFlow.execute(EasyMock.capture(dependeeCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee already ran!?", dependeeRan.compareAndSet(false, true));
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependeeCallback.getValue().completed(Status.FAILED);

                return null;
            }
        }).once();

        EasyMock.replay(dependerFlow, dependeeFlow);

        Assert.assertEquals(Status.READY, flow.getStatus());

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.FAILED, status);
            }
        });

        Assert.assertTrue("Internal flow executes never ran.", dependeeRan.get());
        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.FAILED, flow.getStatus());

        callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.FAILED, status);
            }
        });

        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.FAILED, flow.getStatus());
    }

    @Test
    public void testFailureInDepender() throws Exception
    {
        final AtomicBoolean dependeeRan = new AtomicBoolean(false);

        final Capture<FlowCallback> dependeeCallback = new Capture<FlowCallback>();
        dependeeFlow.execute(EasyMock.capture(dependeeCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee already ran!?", dependeeRan.compareAndSet(false, true));
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependeeCallback.getValue().completed(Status.SUCCEEDED);

                return null;
            }
        }).once();

        final Capture<FlowCallback> dependerCallback = new Capture<FlowCallback>();
        dependerFlow.execute(EasyMock.capture(dependerCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee must run before depender", dependeeRan.get());
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependerCallback.getValue().completed(Status.FAILED);

                return null;
            }
        }).once();

        EasyMock.replay(dependerFlow, dependeeFlow);

        Assert.assertEquals(Status.READY, flow.getStatus());

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.FAILED, status);
            }
        });

        Assert.assertTrue("Internal flow executes never ran.", dependeeRan.get());
        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.FAILED, flow.getStatus());

        callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.FAILED, status);
            }
        });

        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.FAILED, flow.getStatus());
    }

    @Test
    public void testAllExecutesHaveTheirCallbackCalled() throws Exception
    {
        final AtomicBoolean dependeeRan = new AtomicBoolean(false);
        final AtomicBoolean executeCallWhileStateWasRunningHadItsCallbackCalled = new AtomicBoolean(false);

        final Capture<FlowCallback> dependeeCallback = new Capture<FlowCallback>();
        dependeeFlow.execute(EasyMock.capture(dependeeCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee already ran!?", dependeeRan.compareAndSet(false, true));
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                flow.execute(new OneCallFlowCallback(executeCallWhileStateWasRunningHadItsCallbackCalled)
                {
                    @Override
                    protected void theCallback(Status status)
                    {
                    }
                });

                dependeeCallback.getValue().completed(Status.SUCCEEDED);

                Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());

                return null;
            }
        }).once();

        final Capture<FlowCallback> dependerCallback = new Capture<FlowCallback>();
        dependerFlow.execute(EasyMock.capture(dependerCallback));
        EasyMock.expectLastCall().andAnswer(new IAnswer<Object>()
        {
            @Override
            public Object answer() throws Throwable
            {
                Assert.assertTrue("Dependee must run before depender", dependeeRan.get());
                Assert.assertEquals(Status.RUNNING, flow.getStatus());

                dependerCallback.getValue().completed(Status.SUCCEEDED);

                Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());

                return null;
            }
        }).once();

        EasyMock.replay(dependerFlow, dependeeFlow);

        Assert.assertEquals(Status.READY, flow.getStatus());

        AtomicBoolean callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.SUCCEEDED, status);
            }
        });

        Assert.assertTrue("Internal flow executes never ran.", dependeeRan.get());
        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());
        Assert.assertTrue("dependeeFlow, upon completion, sends another execute() call to the flow.  " +
                          "The callback from that execute call was apparently not called.",
                          executeCallWhileStateWasRunningHadItsCallbackCalled.get());

        callbackRan = new AtomicBoolean(false);
        flow.execute(new OneCallFlowCallback(callbackRan) {
            @Override
            protected void theCallback(Status status)
            {
                Assert.assertEquals(Status.SUCCEEDED, status);
            }
        });

        Assert.assertTrue("Callback didn't run.", callbackRan.get());
        Assert.assertEquals(Status.SUCCEEDED, flow.getStatus());
    }

    @Test
    public void testChildren() throws Exception
    {
        EasyMock.replay(dependeeFlow, dependerFlow);

        Assert.assertTrue("ComposedExecutableFlow should have children.", flow.hasChildren());
        Assert.assertEquals(1, flow.getChildren().size());
        Assert.assertEquals(dependeeFlow, flow.getChildren().get(0));
    }
}
