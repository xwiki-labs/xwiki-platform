/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.web;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;

import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.VelocityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.util.Util;

/**
 * Create document action.
 *
 * @version $Id$
 * @since 2.4M2
 */
public class CreateAction extends XWikiAction
{
    /**
     * Log used to report exceptions.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAction.class);

    /**
     * The template provider class, to create documents from templates.
     */
    private static final EntityReference TEMPLATE_PROVIDER_CLASS = new EntityReference("TemplateProviderClass",
        EntityType.DOCUMENT, new EntityReference("XWiki", EntityType.SPACE));

    /**
     * The name of the deprecated space parameter. <br />
     * Note: if you change the value of this variable, change the value of {{@link #TOCREATE_SPACE} to the previous
     * value.
     *
     * @deprecated Use {@value #SPACE_REFERENCE} as parameter name instead.
     */
    @Deprecated
    private static final String SPACE = "space";

    /**
     * The name of the space reference parameter.
     */
    private static final String SPACE_REFERENCE = "spaceReference";

    /**
     * The name of the page parameter.
     */
    private static final String PAGE = "page";

    /**
     * The name parameter.
     */
    private static final String NAME = "name";

    /**
     * The value of the tocreate parameter when a space is to be created. <br />
     * TODO: find a way to give this constant the same value as the constant above without violating checkstyle.
     */
    private static final String TOCREATE_SPACE = SPACE;

    /**
     * The value of the tocreate parameter when a terminal/regular document is to be created.
     */
    private static final String TOCREATE_TERMINAL = "terminal";

    /**
     * The name of the template provider parameter.
     */
    private static final String TEMPLATE_PROVIDER = "templateprovider";

    /**
     * The name of the template field inside the template provider, or the template parameter which can be sent
     * directly, without passing through the template provider.
     */
    private static final String TEMPLATE = "template";

    /**
     * The key used to add exceptions on the context, to be read by the template.
     */
    private static final String EXCEPTION = "createException";

    /**
     * The property name for the template type (page or spaces) in the template provider object.
     */
    private static final String TYPE_PROPERTY = "type";

    /**
     * The property name for the spaces in the template provider object.
     */
    private static final String SPACES_PROPERTY = "spaces";

    /**
     * Internal name for a flag determining if we are creating a Nested Space or a terminal document.
     */
    private static final String IS_SPACE = "isSpace";

    /**
     * Space homepage document name.
     */
    private static final String WEBHOME = "WebHome";

    /**
     * The name of the velocity context in the context, to put variables used in the vms.
     */
    private static final String VELOCITY_CONTEXT_KEY = "vcontext";

    /**
     * Local entity reference serializer hint.
     */
    private static final String LOCAL_SERIALIZER_HINT = "local";

    /**
     * Current entity reference resolver hint.
     */
    private static final String CURRENT_RESOLVER_HINT = "current";

    /**
     * Current entity reference resolver hint.
     */
    private static final String CURRENT_MIXED_RESOLVER_HINT = "currentmixed";

    /**
     * Default constructor.
     */
    public CreateAction()
    {
        this.waitForXWikiInitialization = false;
    }

    @Override
    public String render(XWikiContext context) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        XWikiDocument doc = context.getDoc();
        // resolver to use to resolve references received in request parameters
        DocumentReferenceResolver<String> resolver =
            Utils.getComponent(DocumentReferenceResolver.TYPE_STRING, CURRENT_MIXED_RESOLVER_HINT);

        // Process the request and determine what our target document is.
        Map<String, Object> targetData = getTarget(doc, request);

        SpaceReference spaceReference = (SpaceReference) targetData.get(SPACE_REFERENCE);
        String name = (String) targetData.get(NAME);
        boolean isSpace = (boolean) targetData.get(IS_SPACE);

        // get the template provider for creating this document, if any template provider is specified
        DocumentReferenceResolver<EntityReference> referenceResolver =
            Utils.getComponent(DocumentReferenceResolver.TYPE_REFERENCE, CURRENT_RESOLVER_HINT);
        DocumentReference templateProviderClassReference = referenceResolver.resolve(TEMPLATE_PROVIDER_CLASS);
        BaseObject templateProvider = getTemplateProvider(context, resolver, templateProviderClassReference);

        // get the available templates, in the current space, to check if all conditions to create a new document are
        // met
        List<Document> availableTemplates =
            getAvailableTemplates(doc.getDocumentReference().getLastSpaceReference(), isSpace, resolver,
                templateProviderClassReference, context);
        // put the available templates on the context, for the .vm to not compute them again
        ((VelocityContext) context.get(VELOCITY_CONTEXT_KEY)).put("createAvailableTemplates", availableTemplates);

        // get the reference to the new document
        DocumentReference newDocRef =
            getNewDocumentReference(context, spaceReference, name, isSpace, templateProvider, availableTemplates);

        if (newDocRef != null) {
            // Checking rights to create the new document.
            checkRights(newDocRef.getLastSpaceReference(), context);

            XWikiDocument newDoc = context.getWiki().getDocument(newDocRef, context);
            // if the document exists don't create it, put the exception on the context so that the template gets it and
            // re-requests the page and space, else create the document and redirect to edit
            if (!isEmptyDocument(newDoc)) {
                Object[] args = {newDocRef};
                XWikiException documentAlreadyExists =
                    new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                        XWikiException.ERROR_XWIKI_APP_DOCUMENT_NOT_EMPTY,
                        "Cannot create document {0} because it already has content", null, args);
                ((VelocityContext) context.get(VELOCITY_CONTEXT_KEY)).put(EXCEPTION, documentAlreadyExists);
            } else {
                // create is finally valid, can be executed
                doCreate(context, newDoc, isSpace, templateProvider, resolver);
            }
        }

        return "create";
    }

    private Map<String, Object> getTarget(XWikiDocument doc, XWikiRequest request)
    {
        Map<String, Object> result = new HashMap<>();

        // Since this template can be used for creating a Page or a Space, check the passed "tocreate" parameter
        // which can be either "page" or "space". If no parameter is passed then we default to creating a Page.
        String toCreate = request.getParameter("tocreate");

        SpaceReference spaceReference = null;
        String name = "";
        boolean isSpace = false;

        // Set the space, page, title variables from the current doc if its new, from the passed parameters if any
        if (doc.isNew()) {
            // Current space and page name.
            spaceReference = doc.getDocumentReference().getLastSpaceReference();
            name = doc.getDocumentReference().getName();

            // Always creating terminal documents when using the create action from the URL on inexistent documents.
            isSpace = false;

            // Nothing more to do, stop here.
            setData(spaceReference, name, isSpace, result);

            return result;
        }

        // We are on an existing document...

        String spaceReferenceParameter = request.getParameter(SPACE_REFERENCE);

        if (spaceReferenceParameter != null) {
            // We can have an empty spaceReference parameter symbolizing that we are creating a top level space/ND.
            if (StringUtils.isNotEmpty(spaceReferenceParameter)) {
                EntityReferenceResolver<String> genericResolver =
                    Utils.getComponent(EntityReferenceResolver.TYPE_STRING, CURRENT_RESOLVER_HINT);

                EntityReference resolvedEntityReference =
                    genericResolver.resolve(spaceReferenceParameter, EntityType.SPACE);
                spaceReference = new SpaceReference(resolvedEntityReference);
            }

            name = request.getParameter(NAME);

            isSpace = !TOCREATE_TERMINAL.equals(toCreate);

            // Nothing more to do, stop here.
            setData(spaceReference, name, isSpace, result);

            return result;
        }

        // If no spaceReference parameter is set, then we are in Backwards Compatibility mode and we are using
        // the deprecated parameter names.
        // Note: The deprecated "space" parameter stores unescaped space names, not references!
        String spaceParameter = request.getParameter(SPACE);

        isSpace = TOCREATE_SPACE.equals(toCreate);
        if (isSpace) {
            // Always creating top level spaces in this mode. Adapt to the new implementation.
            spaceReference = null;
            name = spaceParameter;
        } else {
            if (StringUtils.isNotEmpty(spaceParameter)) {
                // Always creating documents in top level spaces in this mode.
                spaceReference = new SpaceReference(spaceParameter, doc.getDocumentReference().getWikiReference());
            }

            name = request.getParameter(PAGE);
        }

        // We are done.
        setData(spaceReference, name, isSpace, result);

        return result;
    }

    /**
     * Utility method to transport results internally and to avoid nesting IF statements more than allowed.
     */
    private void setData(SpaceReference spaceReference, String name, boolean isSpace, Map<String, Object> data)
    {
        data.put(SPACE_REFERENCE, spaceReference);
        data.put(NAME, name);
        data.put(IS_SPACE, isSpace);
    }

    /**
     * @param context the context of the execution of this action
     * @param referenceResolver the reference resolver to use to resolve the parameter value
     * @param templateProviderClass the class of the template provider object
     * @return the object which holds the template provider to be used for creation
     * @throws XWikiException in case anything goes wrong manipulating documents
     */
    private BaseObject getTemplateProvider(XWikiContext context, DocumentReferenceResolver<String> referenceResolver,
        DocumentReference templateProviderClass) throws XWikiException
    {
        // set the template, from the template provider param
        String templateProviderDocReferenceString = context.getRequest().getParameter(TEMPLATE_PROVIDER);
        BaseObject templateProvider = null;
        if (!StringUtils.isEmpty(templateProviderDocReferenceString)) {
            // parse this document reference
            DocumentReference templateProviderRef = referenceResolver.resolve(templateProviderDocReferenceString);
            // get the document of the template provider and the object
            XWikiDocument templateProviderDoc = context.getWiki().getDocument(templateProviderRef, context);
            templateProvider = templateProviderDoc.getXObject(templateProviderClass);
        }
        return templateProvider;
    }

    /**
     * @param spaceReference the space to check if there are available templates for
     * @param isSpace {@code true} if a space should be created instead of a page
     * @param resolver the resolver to solve template document references
     * @param context the context of the current request
     * @param templateClassReference the reference to the template provider class
     * @return the available templates for the passed space, as {@link Document}s
     */
    private List<Document> getAvailableTemplates(SpaceReference spaceReference, boolean isSpace,
        DocumentReferenceResolver<String> resolver, DocumentReference templateClassReference, XWikiContext context)
    {
        XWiki wiki = context.getWiki();
        List<Document> templates = new ArrayList<Document>();
        try {
            EntityReferenceSerializer<String> serializer = Utils.getComponent(EntityReferenceSerializer.TYPE_STRING);
            String spaceStringReference = serializer.serialize(spaceReference);

            QueryManager queryManager = Utils.getComponent((Type) QueryManager.class, "secure");
            Query query =
                queryManager.createQuery("from doc.object(XWiki.TemplateProviderClass) as template "
                    + "where doc.fullName not like 'XWiki.TemplateProviderTemplate'", Query.XWQL);

            // TODO: Extend the above query to include a filter on the type and allowed spaces properties so we can
            // remove the java code below, thus improving performance by not loading all the documents, but only the
            // documents we need.

            List<String> templateProviderDocNames = query.execute();
            for (String templateProviderName : templateProviderDocNames) {
                // get the document
                DocumentReference reference = resolver.resolve(templateProviderName);
                XWikiDocument templateDoc = wiki.getDocument(reference, context);
                BaseObject templateObject = templateDoc.getXObject(templateClassReference);
                if (isSpace && SPACE.equals(templateObject.getStringValue(TYPE_PROPERTY))) {
                    templates.add(new Document(templateDoc, context));
                } else if (!isSpace && !SPACE.equals(templateObject.getStringValue(TYPE_PROPERTY))) {
                    @SuppressWarnings("unchecked")
                    List<String> allowedSpaces = templateObject.getListValue(SPACES_PROPERTY);
                    // if no space is checked or the current space is in the list of allowed spaces
                    if (allowedSpaces.size() == 0 || allowedSpaces.contains(spaceStringReference)) {
                        // create a Document and put it in the list
                        templates.add(new Document(templateDoc, context));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("There was an error gettting the available templates for space {0}", spaceReference, e);
        }

        return templates;
    }

    /**
     * @param context the context to execute this action
     * @param spaceReference the space reference of the new document or the parent space of the new space
     * @param name the page name of the new terminal document or the space name of the space
     * @param isSpace whether the new document to be created is a space homepage (create space / create Nested Document)
     *            or a regular (terminal) page
     * @param templateProvider the template provider for creating this page
     * @param availableTemplates the templates available
     * @return the document reference of the new document to be created, {@code null} if a no document can be created
     *         (because the conditions are not met)
     */
    private DocumentReference getNewDocumentReference(XWikiContext context, SpaceReference spaceReference, String name,
        boolean isSpace, BaseObject templateProvider, List<Document> availableTemplates)
    {
        DocumentReference newDocRef = null;

        if (isSpace) {
            if (StringUtils.isEmpty(name)) {
                // The new space's name is missing.
                return null;
            }

            EntityReference parentReference = spaceReference;
            if (parentReference == null) {
                EntityReference currentWikiReference = context.getDoc().getDocumentReference().getWikiReference();
                parentReference = currentWikiReference;
            }

            // The new space's reference.
            SpaceReference newSpaceReference = new SpaceReference(name, parentReference);

            // The new space's homepage, i.e. Nested Document's reference.
            newDocRef = new DocumentReference(WEBHOME, newSpaceReference);

            // Nothing else to do but return the new reference.
            return newDocRef;
        }

        if (spaceReference == null) {
            // No space specified, nothing to do.
            return null;
        }

        // Proceed with creating a document...

        // Check whether there is a template parameter set, be it an empty one. If a page should be created and there is
        // no template parameter, it means the create action is not supposed to be executed, but only display the
        // available templates and let the user choose
        boolean hasTemplate =
            context.getRequest().getParameterMap().containsKey(TEMPLATE_PROVIDER)
                || context.getRequest().getParameterMap().containsKey(TEMPLATE);
        // If there's no passed template, check if there are any available templates. If none available, then the fact
        // that there is no template is ok.
        if (!hasTemplate) {
            boolean canHasTemplate = availableTemplates.size() > 0;
            hasTemplate = !canHasTemplate;
        }

        if (!StringUtils.isEmpty(name) && hasTemplate) {
            // check if the creation in this space is allowed, and only set the new document reference if the creation
            // is allowed
            if (checkAllowedSpace(spaceReference, name, templateProvider, context)) {
                newDocRef = new DocumentReference(name, spaceReference);
            }
        }

        return newDocRef;
    }

    /**
     * Verifies if the creation inside space {@code space} is allowed by the template provider described by
     * {@code templateProvider}. If the creation is not allowed, an exception will be set on the context.
     *
     * @param spaceReference the reference of the space to create a page in
     * @param page the page to create
     * @param templateProvider the template provider to use for the creation
     * @param context the context of the request
     * @return {@code true} if the creation is allowed, {@code false} otherwise
     */
    private boolean checkAllowedSpace(SpaceReference spaceReference, String page, BaseObject templateProvider,
        XWikiContext context)
    {
        // Check that the chosen space is allowed with the given template, if not:
        // - Cancel the redirect
        // - set an error on the context, to be read by the create.vm
        if (templateProvider != null) {
            @SuppressWarnings("unchecked")
            List<String> allowedSpaces = templateProvider.getListValue(SPACES_PROPERTY);
            // if there is no allowed spaces set, all spaces are allowed
            if (allowedSpaces.size() > 0) {
                EntityReferenceSerializer<String> localSerializer =
                    Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, LOCAL_SERIALIZER_HINT);
                String localSerializedSpace = localSerializer.serialize(spaceReference);
                if (!allowedSpaces.contains(localSerializedSpace)) {
                    // put an exception on the context, for create.vm to know to display an error
                    Object[] args = {templateProvider.getStringValue(TEMPLATE), spaceReference, page};
                    XWikiException exception =
                        new XWikiException(XWikiException.MODULE_XWIKI_STORE,
                            XWikiException.ERROR_XWIKI_APP_TEMPLATE_NOT_AVAILABLE,
                            "Template {0} cannot be used in space {1} when creating page {2}", null, args);
                    VelocityContext vcontext = (VelocityContext) context.get(VELOCITY_CONTEXT_KEY);
                    vcontext.put(EXCEPTION, exception);
                    vcontext.put("createAllowedSpaces", allowedSpaces);
                    return false;
                }
            }
        }
        // if no template is specified, creation is allowed
        return true;
    }

    /**
     * @param context the XWiki context
     * @param spaceReference the reference of the space where the new document will be created
     * @throws XWikiException in case the permission to create a new document in the specified space is denied
     */
    private void checkRights(SpaceReference spaceReference, XWikiContext context) throws XWikiException
    {
        ContextualAuthorizationManager authManager = Utils.getComponent(ContextualAuthorizationManager.class);
        if (!authManager.hasAccess(Right.EDIT, spaceReference)) {
            Object[] args = {spaceReference.toString(), context.getUser()};
            throw new XWikiException(XWikiException.MODULE_XWIKI_ACCESS, XWikiException.ERROR_XWIKI_ACCESS_DENIED,
                "The creation of a document into the space {0} has been denied to user {1}", null, args);
        }
    }

    /**
     * Checks if a document is empty, that is, if a document with that name could be created from a template. <br />
     * TODO: move this function to a more accessible place, to be used by the readFromTemplate method as well, so that
     * we have consistency.
     *
     * @param document the document to check
     * @return {@code true} if the document is empty (i.e. a document with the same name can be created (from
     *         template)), {@code false} otherwise
     */
    private boolean isEmptyDocument(XWikiDocument document)
    {
        // if it's a new document, it's fine
        if (document.isNew()) {
            return true;
        }

        // FIXME: the code below is not really what users might expect. Overriding an existing document (even if no
        // content or objects) is not really nice to do. Should be removed.

        // otherwise, check content and objects (only empty newline content allowed and no objects)
        String content = document.getContent();
        if (!content.equals("\n") && !content.equals("") && !content.equals("\\\\")) {
            return false;
        }

        // go through all the objects and when finding the first one which is not null (because of the remove gaps),
        // return false, we cannot re-create this doc
        for (Map.Entry<DocumentReference, List<BaseObject>> objList : document.getXObjects().entrySet()) {
            for (BaseObject obj : objList.getValue()) {
                if (obj != null) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Actually executes the create, after all preconditions have been verified.
     *
     * @param context the context of this action
     * @param newDocument the document to be created
     * @param isSpace whether the document is a space webhome or a page
     * @param templateProvider the template provider to create from
     * @param resolver the reference resolver to use to resolve template references and other document references
     *            received in parameters
     * @throws XWikiException in case anything goes wrong accessing xwiki documents
     */
    private void doCreate(XWikiContext context, XWikiDocument newDocument, boolean isSpace,
        BaseObject templateProvider, DocumentReferenceResolver<String> resolver) throws XWikiException
    {
        XWikiRequest request = context.getRequest();
        XWikiDocument doc = context.getDoc();
        String parent = getParent(request, doc, isSpace, context);

        // get the title of the page to create, as specified in the parameters
        String title = getTitle(request, newDocument, isSpace);

        // get the template from the template parameter, to allow creation directly from template, without
        // forcing to create a template provider for each template creation
        String template =
            (templateProvider != null) ? templateProvider.getStringValue(TEMPLATE) : (request.getParameterMap()
                .containsKey(TEMPLATE) ? request.getParameter(TEMPLATE) : "");

        // from the template provider, find out if the document should be saved before edited
        boolean toSave = getSaveBeforeEdit(templateProvider);

        String redirectParams = null;
        String editMode = null;
        if (toSave) {
            XWiki xwiki = context.getWiki();

            DocumentReference templateReference = resolver.resolve(template);
            newDocument.readFromTemplate(templateReference, context);
            if (!StringUtils.isEmpty(parent)) {
                DocumentReference parentReference = resolver.resolve(parent);
                newDocument.setParentReference(parentReference);
            }
            if (title != null) {
                newDocument.setTitle(title);
            }
            DocumentReference currentUserReference = context.getUserReference();
            newDocument.setAuthorReference(currentUserReference);
            newDocument.setCreatorReference(currentUserReference);

            xwiki.saveDocument(newDocument, context);
            editMode = newDocument.getDefaultEditMode(context);
        } else {
            // put all the data in the redirect params, to be passed to the edit mode
            redirectParams = "template=" + Util.encodeURI(template, context);
            if (parent != null) {
                redirectParams += "&parent=" + Util.encodeURI(parent, context);
            }
            if (title != null) {
                redirectParams += "&title=" + Util.encodeURI(title, context);
            }

            // Get the edit mode of the document to create from the specified template
            editMode = getEditMode(template, resolver, context);
        }

        // Perform a redirection to the edit mode of the new document
        String redirectURL = newDocument.getURL(editMode, redirectParams, context);
        redirectURL = context.getResponse().encodeRedirectURL(redirectURL);
        if (context.getRequest().getParameterMap().containsKey("ajax")) {
            // If this template is displayed from a modal popup, send a header in the response notifying that a
            // redirect must be performed in the calling page.
            context.getResponse().setHeader("redirect", redirectURL);
        } else {
            // Perform the redirect
            sendRedirect(context.getResponse(), redirectURL);
        }
    }

    /**
     * @param request the current request for which this action is executed
     * @param doc the current document
     * @param isSpace {@code true} if the request is to create a space, {@code false} if a page should be created
     * @param context the XWiki context
     * @return the serialized reference of the parent to create the document for
     */
    private String getParent(XWikiRequest request, XWikiDocument doc, boolean isSpace, XWikiContext context)
    {
        // This template can be passed a parent document reference in parameter (using the "parent" parameter).
        // If a parent parameter is passed, use it to set the parent when creating the new Page or Space.
        // If no parent parameter was passed:
        // * use the current document
        // ** if we're creating a new page and if the current document exists or
        // * use the Main space's WebHome
        // ** if we're creating a new space or
        // ** if we're creating a new page and the current document does not exist.
        String parent = request.getParameter("parent");
        if (StringUtils.isEmpty(parent)) {
            EntityReferenceSerializer<String> localSerializer =
                Utils.getComponent(EntityReferenceSerializer.TYPE_STRING, LOCAL_SERIALIZER_HINT);

            if (isSpace || doc.isNew()) {
                // Use the Main space's WebHome.
                Provider<DocumentReference> defaultDocumentReferenceProvider =
                    Utils.getComponent(DocumentReference.TYPE_PROVIDER);

                DocumentReference parentRef =
                    defaultDocumentReferenceProvider.get().setWikiReference(context.getWikiReference());

                parent = localSerializer.serialize(parentRef);
            } else {
                // Use the current document.
                DocumentReference parentRef = doc.getDocumentReference();

                parent = localSerializer.serialize(parentRef);
            }
        }

        return parent;
    }

    /**
     * @param request the current request for which this action is executed
     * @param newDocument the document to be created
     * @param isSpace {@code true} if the request is to create a space, {@code false} if a page should be created
     * @return the title of the page to be created. If no request parameter is set, the page name is returned for a new
     *         page and the space name is returned for a new space
     */
    private String getTitle(XWikiRequest request, XWikiDocument newDocument, boolean isSpace)
    {
        String title = request.getParameter("title");
        if (StringUtils.isEmpty(title)) {
            if (isSpace) {
                title = newDocument.getDocumentReference().getLastSpaceReference().getName();
            } else {
                title = newDocument.getDocumentReference().getName();
                // Avoid WebHome titles for pages that are rally space homepages.
                if (WEBHOME.equals(title)) {
                    title = newDocument.getDocumentReference().getLastSpaceReference().getName();
                }
            }
        }

        return title;
    }

    /**
     * @param templateProvider the template provider for this creation
     * @return {@code true} if the created document should be saved on create, before editing, {@code false} otherwise
     */
    boolean getSaveBeforeEdit(BaseObject templateProvider)
    {
        boolean toSave = false;

        if (templateProvider != null) {
            // get the action to execute and compare it to saveandedit value
            String action = templateProvider.getStringValue("action");
            if ("saveandedit".equals(action)) {
                toSave = true;
            }
        }

        return toSave;
    }

    /**
     * @param template the template to create document from
     * @param resolver the resolver to use to resolve the template document reference
     * @param context the context of the current request
     * @return the default edit mode for a document created from the passed template
     * @throws XWikiException in case something goes wrong accessing template document
     */
    private String getEditMode(String template, DocumentReferenceResolver<String> resolver, XWikiContext context)
        throws XWikiException
    {
        // Determine the edit action (edit/inline) for the newly created document, if a template is passed it is
        // used to determine the action. Default is 'edit'.
        String editAction = "edit";
        XWiki xwiki = context.getWiki();
        if (!StringUtils.isEmpty(template)) {
            DocumentReference templateReference = resolver.resolve(template);
            if (xwiki.exists(templateReference, context)) {
                editAction = xwiki.getDocument(templateReference, context).getDefaultEditMode(context);
            }
        }

        return editAction;
    }
}
