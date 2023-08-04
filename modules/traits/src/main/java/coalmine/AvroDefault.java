package coalmine;

            import software.amazon.smithy.model.traits.AbstractTrait;
         import software.amazon.smithy.model.node.Node;
         import software.amazon.smithy.model.shapes.ShapeId;
            import software.amazon.smithy.model.traits.Trait;

/**
 * Provides a default value for a shape or member.
 */
public final class AvroDefault extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("coalmine#avroDefault");

    public AvroDefault(Node value) {
        super(ID, value);
    }

    @Override
    protected Node createNode() {
        throw new UnsupportedOperationException("NodeCache is always set");
    }

    public static final class Provider extends AbstractTrait.Provider {
        public Provider() {
            super(ID);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            return new AvroDefault(value);
        }
    }
}