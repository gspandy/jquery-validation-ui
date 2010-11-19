/* Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.jquery.validation.ui

import org.codehaus.groovy.grails.validation.ConstrainedPropertyBuilder
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import grails.util.GrailsNameUtils

/**
 *
 * @author <a href='mailto:limcheekin@vobject.com'>Lim Chee Kin</a>
 *
 * @since 0.1
 */
class JQueryValidationUiTagLib {
    static namespace = "jqvalui"
    static final EXCLUDED_DECLARED_FIELDS = [
        "id",
        "version",
        "metaClass",
        "constraints",
        "mapping",
        "embedded"
    ]
    static final DEFAULT_ERROR_MESSAGE_CODES_MAP = [
        matches: "default.doesnt.match.message",
        url: "default.invalid.url.message",
        creditCard: "default.invalid.creditCard.message",
        email: "default.invalid.email.message",
        range: "default.invalid.range.message",
        size: "default.invalid.size.message",
        max: "default.invalid.max.message",
        min: "default.invalid.min.message",
        maxSize: "default.invalid.max.size.message",
        minSize: "default.invalid.min.size.message",
        validator: "default.invalid.validator.message",
        inList: "default.not.inlist.message",
        blank: "default.blank.message",
        notEqual: "default.not.equal.message",
        nullable: "default.null.message",
        unique: "default.not.unique.message"
    ]
	
    static final String TAG_ERROR_PREFIX = "Tag [jqvalui:renderValidationScript] Error: "
	
    def resources = { attrs, body ->
        def packed = grailsApplication.config.jqueryValidationUi.qTip.get("packed", true)
        def url = g.resource(plugin:"jqueryValidationUi", dir:"js/qTip", file:"jquery.qtip.${packed?'pack.js':'js'}")
        out << "<script type=\"text/javascript\" src=\"${url}\"></script>\n"
        url = g.resource(plugin:"jqueryValidationUi", dir:"css/qTip", file:"jquery.qtip.css")
        out << "<link rel=\"stylesheet\" type=\"text/css\" media=\"screen\" href=\"${url}\" />\n"
        url = g.resource(plugin:"jqueryValidationUi", dir:"js/jquery-validation-ui", file:"grails-validation-methods.js")
        out << "<script type=\"text/javascript\" src=\"${url}\"></script>\n"
    }
	
    def renderValidationScript = { attrs, body ->
        String validatableClassName = attrs.remove("validatableClass")
        if (!validatableClassName) {
            throwTagError("${TAG_ERROR_PREFIX}Tag missing required attribute [validatableClass]")
        }
        def validatableClass = grailsApplication.classLoader.loadClass(validatableClassName)
        if (!validatableClass) {
            throwTagError("${TAG_ERROR_PREFIX}Invalid validatableClass, $validatableClassName not found!")
            return
        }
        def constraintsProperties = getConstraintsProperties(validatableClass)
        out << '<script type="text/javascript">\n'
        out << """\$(function() {
var myForm = \$('form:first');
myForm.validate({
debug: true,
onkeyup: false,
errorClass: 'error',
validClass: 'valid',
submitHandler: function(form) {
   form.submit();
},				
success: function(label)
{
	\$('#' + label.attr('for')).qtip('destroy');
},
errorPlacement: function(error, element)
{
	if (\$(error).text())
	\$(element).filter(':not(.valid)').qtip({
		overwrite: true,
		content: error,
		position: { my: 'left center', at: 'right center' },
		show: {
			event: false,
			ready: true
		},
		hide: false,
		style: {
			// widget: true,
			classes: 'ui-tooltip-red',
			tip: true
		}
	});
},
rules: {
"""
        constraintsProperties.each { k, constrainedProperty  ->
            out << createJavaScriptConstraints(constrainedProperty)
        }
        out << "},\n" // end rules
        out << "messages: {\n"
        constraintsProperties.each { k, constrainedProperty  ->
            out << createJavaScriptMessages(validatableClass, constrainedProperty)
        }
        out << "}\n" // end messages
        out << "});\n"
        out << "});\n"
        out << "</script>\n"
    }
	
    private Map getConstraintsProperties(Class validatableClass) {
        def constraintsProperties
        if (!validatableClass.constraints) {
            throwTagError("${TAG_ERROR_PREFIX}Invalid validatableClass, constraints closure undefined!")
        }
        if (validatableClass.constraints instanceof Closure) {
            def validationClosure = validatableClass.constraints
            def constrainedPropertyBuilder = new ConstrainedPropertyBuilder(validatableClass.newInstance())
            validationClosure.setDelegate(constrainedPropertyBuilder)
            validationClosure()
            constrainedProperties = constrainedPropertyBuilder.constrainedProperties
        } else {
            constraintsProperties = validatableClass.constraints
        }
        return constraintsProperties
    }
	
    private Map getConstraintsMap(Class propertyType) {
        def constraintsMap
        if (propertyType == String) {
            constraintsMap = grailsApplication.config.jqueryValidationUi.StringConstraintsMap
        } else if (propertyType == Date) {
            constraintsMap = grailsApplication.config.jqueryValidationUi.DateConstraintsMap
        } else if (propertyType.superclass == Number) {
            constraintsMap = grailsApplication.config.jqueryValidationUi.NumberConstraintsMap
        } else if (propertyType.interfaces.contains(Collection)) {
            constraintsMap = grailsApplication.config.jqueryValidationUi.CollectionConstraintsMap
        }
        return constraintsMap
    }
	
    private String createJavaScriptConstraints(def constrainedProperty) {
        String javaScriptConstraints = "${constrainedProperty.propertyName}: {\n"
        def constraintsMap = getConstraintsMap(constrainedProperty.propertyType)
        String javaScriptConstraint
		
        if (constrainedProperty.propertyType == Date) {
            javaScriptConstraints += "\tdate: true,\n"
        }
		
        def constraintNames = constrainedProperty.appliedConstraints.collect { return it.name }
        if (constraintNames.contains("blank") && constraintNames.contains("nullable")) {
            constraintNames.remove("nullable")
        }
        constraintNames.each { constraintName ->
            javaScriptConstraint = constraintsMap[constraintName]
            if (javaScriptConstraint) {
                switch (constraintName) {
                    case "nullable":
                    case "blank":
                    if (!constrainedProperty.isBlank() || !constrainedProperty.isNullable()) {
                        javaScriptConstraints += "\t${javaScriptConstraint}: true,\n"
                    }
                    break
                    case "creditCard":
                    if (constrainedProperty.isCreditCard()) {
                        javaScriptConstraints += "\t${javaScriptConstraint}: true,\n"
                    }
                    break
                    case "email":
                    if (constrainedProperty.isEmail()) {
                        javaScriptConstraints += "\t${javaScriptConstraint}: true,\n"
                    }
                    break
                    case "url":
                    if (constrainedProperty.isUrl()) {
                        javaScriptConstraints += "\t${javaScriptConstraint}: true,\n"
                    }
                    break
                    case "inList":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ["
                    if (constrainedProperty.propertyType == Date) {
                        constrainedProperty.inList.each { javaScriptConstraints += "new Date(${it.time})," }
                    } else {
                        constrainedProperty.inList.each { javaScriptConstraints += "'${it}'," }
                    }
                    javaScriptConstraints += "],\n"
                    break
                    case "matches":
                    javaScriptConstraints += "\t${javaScriptConstraint}: '${constrainedProperty.matches}',\n"
                    break
                    case "max":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ${constrainedProperty.propertyType == Date ? "new Date(${constrainedProperty.max.time})" : constrainedProperty.max},\n"
                    break
                    case "maxSize":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ${constrainedProperty.maxSize},\n"
                    break
                    case "min":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ${constrainedProperty.propertyType == Date ? "new Date(${constrainedProperty.min.time})" : constrainedProperty.min},\n"
                    break
                    case "minSize":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ${constrainedProperty.minSize},\n"
                    break
                    case "notEqual":
                    javaScriptConstraints += "\t${javaScriptConstraint}: ${constrainedProperty.propertyType == Date ? "new Date(${constrainedProperty.notEqual.time})" : "'${constrainedProperty.notEqual}'"},\n"
                    break
                    case "range":
                    def range = constrainedProperty.range
                    if (constrainedProperty.propertyType == Date) {
                        javaScriptConstraints += "\t${javaScriptConstraint}: [new Date(${range.from.time}), new Date(${range.to.time})],\n"
                    } else {
                        javaScriptConstraints += "\t${javaScriptConstraint}: [${range.from}, ${range.to}],\n"
                    }
                    break
                    case "size":
                    def size = constrainedProperty.size
                    javaScriptConstraints += "\t${javaScriptConstraint}: [${size.from}, ${size.to}],\n"
                    break
                }
            } else {
                println "${constraintName} constraint not found in the constraintsMap, remote validation"
            }
        }
        javaScriptConstraints += "},\n"
        // println "constrainedProperty.isPassword() = ${constrainedProperty.isPassword()}"
        return javaScriptConstraints
    }

    private String createJavaScriptMessages(Class validatableClass, def constrainedProperty) {
        def constraintsMap = getConstraintsMap(constrainedProperty.propertyType)
        def args = []
        String javaScriptMessages = "${constrainedProperty.propertyName}: {\n"
        String javaScriptMessage
        def constraintNames = constrainedProperty.appliedConstraints.collect { return it.name }
        if (constraintNames.contains("blank") && constraintNames.contains("nullable")) {
            constraintNames.remove("nullable")
        }

        constraintNames.each { constraintName ->
            javaScriptMessage = constraintsMap[constraintName]
            if (javaScriptMessage) {
                args.clear()
                args = [constrainedProperty.propertyName, validatableClass.simpleName]
                switch (constraintName) {
                    case "nullable":
                    case "blank":
                    if (!constrainedProperty.isBlank() || !constrainedProperty.isNullable()) {
                        javaScriptMessages += "\t${javaScriptMessage}: '${getMessage(validatableClass, constrainedProperty.propertyName, args, constraintName)}',\n"
                    }
                    break
                    case "creditCard":
                    case "email":
                    case "url":
                    if (constrainedProperty.isCreditCard() || constrainedProperty.isEmail() || constrainedProperty.isUrl()) {
                        args << "' + \$('#${constrainedProperty.propertyName}').val() + '"
                        javaScriptMessages += "\t${javaScriptMessage}: function() { return '${getMessage(validatableClass, constrainedProperty.propertyName, args, constraintName)}'; },\n"
                    }
                    break
                    case "inList":
                    case "matches":
                    case "max":
                    case "maxSize":
                    case "min":
                    case "minSize":
                    case "notEqual":
                    args << "' + \$('#${constrainedProperty.propertyName}').val() + '"
                    args << constrainedProperty."${constraintName}"
                    javaScriptMessages += "\t${javaScriptMessage}: function() { return '${getMessage(validatableClass, constrainedProperty.propertyName, args, constraintName)}'; },\n"
                    break

                    case "range":
                    case "size":
                    args << "' + \$('#${constrainedProperty.propertyName}').val() + '"
                    def range = constrainedProperty."${constraintName}"
                    args << range.from
                    args << range.to
                    javaScriptMessages += "\t${javaScriptMessage}: function() { return '${getMessage(validatableClass, constrainedProperty.propertyName, args, constraintName)}'; },\n"
                    break
                }
            } else {
                javaScriptMessage = "remote"
                javaScriptMessages += "\t${javaScriptMessage}: function() { return '${getMessage(validatableClass, constrainedProperty.propertyName, args, constraintName)}'; },\n"
            }
				
        }
        javaScriptMessages += "},\n"
    }
    private String getMessage(Class validateableClass, String propertyName, def args, String constraintName) {
        def messageSource = grailsAttributes.getApplicationContext().getBean("messageSource")
        def locale = RCU.getLocale(request)
        def code = "${validateableClass.name}.${propertyName}.${constraintName}"
		    def defaultMessage = "Error message for ${code} undefined."
		    def message = messageSource.getMessage(code, args == null ? null : args.toArray(), null, locale)
			  if (!message) {
				  code = "${GrailsNameUtils.getPropertyName(validateableClass)}.${propertyName}.${constraintName}"
				  message = messageSource.getMessage(code, args == null ? null : args.toArray(), null, locale)
			  }
			 if (!message) {
		    code = DEFAULT_ERROR_MESSAGE_CODES_MAP[constraintName]
        message = messageSource.getMessage(code, args == null ? null : args.toArray(), defaultMessage, locale)
			  }
		   return message
    }
}

