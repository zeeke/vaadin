package com.vaadin.server;

import java.io.IOException;

import com.vaadin.ui.AbstractComponent;

/**
 * Extension that starts a download when the extended component is clicked. This
 * is used to overcome two challenges:
 * <ul>
 * <li>Resource should be bound to a component to allow it to be garbage
 * collected when there are no longer any ways of reaching the resource.</li>
 * <li>Download should be started directly when the user clicks e.g. a Button
 * without going through a server-side click listener to avoid triggering
 * security warnings in some browsers.</li>
 * </ul>
 * <p>
 * Please note that the download will be started in an iframe, which means that
 * care should be taken to avoid serving content types that might make the
 * browser attempt to show the content using a plugin instead of downloading it.
 * Connector resources (e.g. {@link FileResource} and {@link ClassResource})
 * will automatically be served using a
 * <code>Content-Type: application/octet-stream</code> header unless
 * {@link #setOverrideContentType(boolean)} has been set to <code>false</code>
 * while files served in other ways, (e.g. {@link ExternalResource} or
 * {@link ThemeResource}) will not automatically get this treatment.
 * </p>
 * 
 * @author Vaadin Ltd
 * @since 7.0.0
 */
public class FileDownloader extends AbstractExtension {

    private boolean overrideContentType = true;

    /**
     * Creates a new file downloader for the given resource. To use the
     * downloader, you should also {@link #extend(AbstractClientConnector)} the
     * component.
     * 
     * @param resource
     *            the resource to download when the user clicks the extended
     *            component.
     */
    public FileDownloader(Resource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource may not be null");
        }
        setResource("dl", resource);
    }

    public void extend(AbstractComponent target) {
        super.extend(target);
    }

    /**
     * Gets the resource set for download.
     * 
     * @return the resource that will be downloaded if clicking the extended
     *         component
     */
    public Resource getFileDownloadResource() {
        return getResource("dl");
    }

    /**
     * Sets whether the content type of served resources should be overriden to
     * <code>application/octet-stream</code> to reduce the risk of a browser
     * plugin choosing to display the resource instead of downloading it. This
     * is by default set to <code>true</code>.
     * <p>
     * Please note that this only affects Connector resources (e.g.
     * {@link FileResource} and {@link ClassResource}) but not other resource
     * types (e.g. {@link ExternalResource} or {@link ThemeResource}).
     * </p>
     * 
     * @param overrideContentType
     *            <code>true</code> to override the content type if possible;
     *            <code>false</code> to use the original content type.
     */
    public void setOverrideContentType(boolean overrideContentType) {
        this.overrideContentType = overrideContentType;
    }

    /**
     * Checks whether the content type should be overridden.
     * 
     * @see #setOverrideContentType(boolean)
     * 
     * @return <code>true</code> if the content type will be overridden when
     *         possible; <code>false</code> if the original content type will be
     *         used.
     */
    public boolean isOverrideContentType() {
        return overrideContentType;
    }

    @Override
    public boolean handleConnectorRequest(VaadinRequest request,
            VaadinResponse response, String path) throws IOException {
        if (!path.matches("dl(/.*)?")) {
            // Ignore if it isn't for us
            return false;
        }

        Resource resource = getFileDownloadResource();
        if (resource instanceof ConnectorResource) {
            DownloadStream stream = ((ConnectorResource) resource).getStream();

            if (stream.getParameter("Content-Disposition") == null) {
                // Content-Disposition: attachment generally forces download
                stream.setParameter("Content-Disposition",
                        "attachment; filename=\"" + stream.getFileName() + "\"");
            }

            // Content-Type to block eager browser plug-ins from hijacking the
            // file
            if (isOverrideContentType()) {
                stream.setContentType("application/octet-stream;charset=UTF-8");
            }
            stream.writeResponse(request, response);
            return true;
        } else {
            return false;
        }
    }
}