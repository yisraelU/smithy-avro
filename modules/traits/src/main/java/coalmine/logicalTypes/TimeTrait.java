package coalmine.logicalTypes;

import software.amazon.smithy.model.FromSourceLocation;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.AbstractTrait;
import software.amazon.smithy.model.traits.Trait;

public class TimeTrait extends AbstractTrait {

    public static final ShapeId ID = ShapeId.from("coalmine#avroTime");

    enum TimeType {
        DATE,
        TIME_MILLIS,
        TIME_MICROS,
        TIMESTAMP_MILLIS,
        TIMESTAMP_MICROS,
        LOCAL_TIMESTAMP_MILLIS,
        LOCAL_TIMESTAMP_MICROS
    }

    private TimeType timeType;

    public TimeTrait(TimeType timeType, FromSourceLocation sourceLocation) {
        super(ID, sourceLocation);
        this.timeType = timeType;
    }

    public TimeType getTimeType() {
        return timeType;
    }

    public void setTimeType(TimeType timeType) {
        this.timeType = timeType;
    }

    public TimeTrait(ShapeId id, FromSourceLocation sourceLocation) {
        super(id, sourceLocation);
    }

    @Override
    protected Node createNode() {
        return null;
    }

    public static final class Provider extends AbstractTrait.Provider{
        /**
         * @param id ID of the trait that the provider creates.
         */
        public Provider(ShapeId id) {
            super(id);
        }

        @Override
        public Trait createTrait(ShapeId target, Node value) {
            return null;
        }
    }
}
