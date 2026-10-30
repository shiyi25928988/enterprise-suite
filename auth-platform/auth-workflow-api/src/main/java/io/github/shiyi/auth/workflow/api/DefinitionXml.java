package io.github.shiyi.auth.workflow.api;

/**
 * The BPMN source of a deployed process definition — the file the engine
 * actually deployed, not a re-serialisation of the parsed model, so it can be
 * loaded into the designer and redeployed unchanged.
 *
 * <p>{@code version} is echoed so the designer can say which revision it just
 * opened: editing "v2" while someone else deploys "v3" is otherwise invisible.</p>
 *
 * <p>{@code renderable} is false when the deployment carries no BPMN diagram
 * interchange section (no {@code BPMNDiagram}): the engine runs such a process
 * perfectly, but a canvas has nothing to draw — bpmn-js answers "no diagram to
 * display". Callers should say so instead of handing the XML to the renderer.</p>
 */
public record DefinitionXml(String processKey, String name, int version,
                            boolean renderable, String xml) {
}
