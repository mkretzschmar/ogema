/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ogema.tools.impl;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import org.ogema.core.channelmanager.measurements.BooleanValue;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.IntegerValue;
import org.ogema.core.channelmanager.measurements.LongValue;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.channelmanager.measurements.StringValue;
import org.ogema.core.channelmanager.measurements.Value;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.model.units.PhysicalUnit;
import org.ogema.core.model.units.PhysicalUnitResource;
import org.ogema.core.tools.SerializationManager;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Eric Sternberg (esternberg), cnoelle
 * To replace {@link FastJsonGenerator}
 */
public class StaticJsonGenerator {

	final static JsonFactory jsonFactory = new JsonFactory();

	/**
	 *
	 * @param resource
	 * @param serializationManager
	 * @return String of serialized json limited by options, setup in serializationManager
	 * @throws IOException
	 */
	public static String serialize(Resource resource, SerializationManager serializationManager) throws IOException {
		final StringWriter writer = new StringWriter();
		serialize(writer, resource, serializationManager);
		// writer.flush();
		return writer.toString();
	}

	/**
	 *
	 * @param writer
	 * @param resource
	 * @param serializationManager
	 * @throws IOException
	 */
	public static void serialize(Writer writer, Resource resource, SerializationManager serializationManager)
			throws IOException {
		JsonGenerator jGen = jsonFactory.createGenerator(writer).useDefaultPrettyPrinter();
		StateController stateControl = new StateController(serializationManager, resource); 
		try {
			serializeResource(jGen,stateControl,resource);
		} catch (Throwable e) {
			LoggerFactory.getLogger(StaticJsonGenerator.class).error("error in serialization", e);
		}
		jGen.flush();
	}

	/**
	 *
	 * @param writer
	 * @param obj
	 * @param serializationManager
	 * @throws IOException
	 */
	/* FIXME?: this will always serialize embedded schedules (timeseries) as link */
	public static void serialize(Writer writer, Object obj, SerializationManager serializationManager) throws IOException {
		ObjectMapper mapper = SerializationCore.createJacksonMapper(true);
		JsonGenerator jGen = jsonFactory.createGenerator(writer).useDefaultPrettyPrinter().setCodec(mapper);
		jGen.writeObject(obj);
		jGen.flush();
	}

	/**
	 * Beginning of the real serialization
	 *
	 * @param resource
	 * @throws IOException
	 */
	static void serializeResource(JsonGenerator jGen, StateController stateControl, Resource resource) throws IOException {
		if (stateControl.doDescent(resource)) {
			// true: unknown resource, serialize full and recursively proceed
			serializeFullResource(jGen, stateControl, resource);
		}
		else {
			serializeResourceAsLink(jGen,resource);
		}
		stateControl.decreaseDepth();
	}

	/**
	 * Serializes all resource-fields and subresources
	 *
	 * @param resource
	 * @throws IOException
	 */
	private static void serializeFullResource(JsonGenerator jGen, StateController stateControl, Resource resource) throws IOException {
		// begin serialize Resource
		jGen.writeStartObject();

		// the resource type occures as "@type" element just in the requested root-resource
		if (stateControl.isRquestedRootResource(resource)) {
			jGen.writeStringField("@type", figureOutResourceType(stateControl, resource));
			writeResourceBody(jGen, stateControl, resource);
		}
		else {
			jGen.writeObjectFieldStart(figureOutResourceType(stateControl, resource));
			writeResourceBody(jGen,stateControl, resource);
			jGen.writeEndObject();
		}

		// end serialize Resource
		jGen.writeEndObject();
	}

	/**
	 * Writes all fields of an resource, it calls serializeSubResources(resource) to rekursively create json for each
	 * subresource
	 *
	 * @param resource
	 * @throws IOException
	 */
	private static void writeResourceBody(JsonGenerator jGen, StateController stateControl, Resource resource) throws IOException {
		writeSimpleResourceValue(jGen, resource);
		jGen.writeStringField("name", resource.getName());
		jGen.writeStringField("type", resource.getResourceType().getCanonicalName());
		jGen.writeStringField("path", resource.getPath());
		jGen.writeBooleanField("decorating", resource.isDecorator());
		jGen.writeBooleanField("active", resource.isActive());
		jGen.writeBooleanField("referencing", resource.isReference(true));
		if (resource instanceof ResourceList) {
			@SuppressWarnings("unchecked")
			Class<? extends Resource> listType = ((ResourceList) resource).getElementType();
			jGen.writeStringField("elementType", listType != null ? listType.getName() : null);
		}
		// NOTE: There is no Type of Schedule in Ogema api, that contains all xsd-dfined values
		if (resource instanceof Schedule) {
			serializeEntrys(jGen, (Schedule) resource);
		}
		// serialize subresources
		serializeSubResources(jGen, stateControl, resource);
	}

	/**
	 * determines the type of the given Resource and if its some kind of simpe resource, it writes the corresponding
	 * value-field to json, else it does nothing
	 *
	 * @param resource
	 * @throws IOException
	 */
	@SuppressWarnings("deprecation")
	private static void writeSimpleResourceValue(JsonGenerator jGen, Resource resource) throws IOException {
		if (!(resource instanceof SingleValueResource)) {
			return;
		}
		if (resource instanceof BooleanResource) {
			jGen.writeBooleanField("value", ((BooleanResource) resource).getValue());
		}
		else if (resource instanceof FloatResource) {
			jGen.writeNumberField("value", ((FloatResource) resource).getValue());
			if (resource instanceof PhysicalUnitResource) {
				PhysicalUnit u = ((PhysicalUnitResource) resource).getUnit();
				if (u != null) {
					jGen.writeStringField("unit", u.toString());
				}
			}
		}
		else if (resource instanceof IntegerResource) {
			jGen.writeNumberField("value", ((IntegerResource) resource).getValue());
		}
		else if (resource instanceof TimeResource) {
			jGen.writeNumberField("value", ((TimeResource) resource).getValue());
		}
		else if (resource instanceof StringResource) {
			jGen.writeStringField("value", ((StringResource) resource).getValue());
		}
		else if (resource instanceof org.ogema.core.model.simple.OpaqueResource) {
			// Jackson default Base64 variant (which is Base64Variants.MIME_NO_LINEFEEDS)
			jGen.writeBinaryField("value", ((org.ogema.core.model.simple.OpaqueResource) resource).getValue());
		}
	}

	/**
	 * Just writes the fields to json needed for a resource-link. No further rekursive calls are done.
	 *
	 * @param resource
	 * @throws IOException
	 */
	private static void serializeResourceAsLink(JsonGenerator jGen, Resource resource) throws IOException {
		jGen.writeStartObject();
		jGen.writeObjectFieldStart("resourcelink");
		jGen.writeStringField("link", resource.getLocation());
		jGen.writeStringField("type", resource.getResourceType().getCanonicalName());
		jGen.writeStringField("name", resource.getName());
		jGen.writeEndObject();
		jGen.writeEndObject();
	}

	/**
	 * Recursively creates json for all subresources by calling this.serializeResource(subRes)
	 *
	 * @param resource
	 * @throws IOException
	 */
	private static void serializeSubResources(JsonGenerator jGen, StateController stateControl, Resource resource) throws IOException {
		jGen.writeArrayFieldStart("subresources");
		// rekursivly write out the subresources, important to get them non-rekursive
		for (Resource subRes : resource.getSubResources(false)) {
			serializeResource(jGen, stateControl, subRes);
		}
		jGen.writeEndArray();
	}

	private static void serializeEntrys(JsonGenerator jGen, Schedule definitionSchedule) throws IOException {
		serializeEntrys(jGen, definitionSchedule, 0, Long.MAX_VALUE);
	}

	@SuppressWarnings("deprecation")
	private static void serializeEntrys(JsonGenerator jGen, Schedule definitionSchedule, long start, long end) throws IOException {
		if (definitionSchedule.getTimeOfLatestEntry() != null) {
			jGen.writeNumberField("lastUpdateTime", definitionSchedule.getTimeOfLatestEntry());
		}
		if (definitionSchedule.getLastCalculationTime() != null) {
			jGen.writeNumberField("lastCalculationTime", definitionSchedule.getLastCalculationTime());
		}
		List<SampledValue> entries = definitionSchedule.getValues(start, end);
		jGen.writeNumberField("start", start);
		jGen.writeNumberField("end", end);
		jGen.writeArrayFieldStart("entry");
		// write sampled values
		if (!entries.isEmpty()) {
			SampledValuesWriter w = SampledValuesWriter.forValue(entries.get(0));
			for (SampledValue sampledValue : entries) {
				w.write(sampledValue, jGen);
			}
		}
		jGen.writeEndArray();
	}

	enum SampledValuesWriter {

		BooleanWriter(BooleanValue.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeBooleanField("value", v.getValue().getBooleanValue());
				jGen.writeStringField("@type", "SampledBoolean");
			}
		},
		FloatWriter(FloatValue.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeNumberField("value", v.getValue().getFloatValue());
				jGen.writeStringField("@type", "SampledFloat");
			}
		},
		IntWriter(IntegerValue.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeNumberField("value", v.getValue().getIntegerValue());
				jGen.writeStringField("@type", "SampledInteger");
			}
		},
		LongWriter(LongValue.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeNumberField("value", v.getValue().getLongValue());
				jGen.writeStringField("@type", "SampledLong");
			}
		},
		StringWriter(StringValue.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeStringField("value", v.getValue().getStringValue());
				jGen.writeStringField("@type", "SampledString");
			}
		},
		DefaultWriter(Value.class) {
			@Override
			public void writeValue(SampledValue v, JsonGenerator jGen) throws IOException {
				jGen.writeStringField("value", v.getValue().getStringValue());
				jGen.writeStringField("@type", "SampledString");
			}
		};

		Class<? extends Value> type;

		SampledValuesWriter(Class<? extends Value> type) {
			this.type = type;
		}

		public static SampledValuesWriter forValue(SampledValue v) {
			Class<?> valueType = v.getValue().getClass();
			for (SampledValuesWriter writer : values()) {
				if (valueType.isAssignableFrom(writer.type)) {
					return writer;
				}
			}
			return DefaultWriter;
		}

		// FIXME this misses the type of the field... leads to failure
		void write(SampledValue val, JsonGenerator jGen) throws IOException {
			jGen.writeStartObject();
			jGen.writeNumberField("time", val.getTimestamp());
			jGen.writeStringField("quality", val.getQuality().name());
			writeValue(val, jGen);
			jGen.writeEndObject();
		}

		protected abstract void writeValue(SampledValue v, JsonGenerator jGen) throws IOException;
	}

	/**
	 * Return the value for the json field "type". If its some kind of simple resource it will return the classname of
	 * that resource (e.g. BooleanResource), else it will return "Resource"
	 *
	 * @param resource
	 * @return String
	 */
	private static String figureOutResourceType(StateController stateControl, Resource resource) {

		if (resource instanceof SingleValueResource || resource instanceof ResourceList) {
			if (resource instanceof FloatResource) {
				return FloatResource.class.getSimpleName();
			}
			return resource.getResourceType().getSimpleName();
		}
		if (resource instanceof Schedule) {
			return figureOutScheduleType((Schedule) resource);
		}
		// return "Resource";
		// TODO: clarify why different cases in old json-serialization?
		return stateControl.isRquestedRootResource(resource) ? "Resource" : "resource"; // FIXME this is annoying
	}

	/**
	 * We have to figgure out which (simple)type of Schedule we have ( (but there are no simple typs of schedules in the
	 * ogema model) otherwise deserialization of a schedule wont work.
	 *
	 * @param schedule
	 * @return
	 */
	private static String figureOutScheduleType(Schedule schedule) {
		final Resource parent = schedule.getParent();
		if (parent instanceof BooleanResource) {
			return "BooleanSchedule";
		}
		if (parent instanceof FloatResource) {
			return "FloatSchedule";
		}
		if (parent instanceof IntegerResource) {
			return "IntegerSchedule";
		}
		if (parent instanceof org.ogema.core.model.simple.StringResource) {
			return "StringSchedule";
		}
		if (parent instanceof org.ogema.core.model.simple.TimeResource) {
			return "TimeSchedule";
		}
		// NOTE deserialization of type shedule wont work
		return "Schedule";
	}
		
}
