package edu.uva.liftlab.recoverychecker.isolation.IO;

import java.util.Arrays;
import java.util.List;

class CompositeIOHandler implements IOOperationHandler {
    private final List<IOOperationHandler> handlers;

    public CompositeIOHandler() {
        this.handlers = Arrays.asList(
                new FileChannelHandler(),
                new FileConstructorHandler(),
                new FileOutputStreamHandler(),
                new FilesDeleteHandler(),
                new BufferedWriterHandler()
        );
    }

    @Override
    public boolean handle(IOContext context) {
        for (IOOperationHandler handler : handlers) {
            if (handler.handle(context)) {
                return true;
            }
        }
        return false;
    }
}
