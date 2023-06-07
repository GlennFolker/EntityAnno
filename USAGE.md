## Usage
### Structure
In `EntityAnno`, entity class generation is done very similarly like in `Mindustry`:
1. Users define `*Comp`onent classes, complete with its owned field and method definitions.
2. From these `*Comp`onent classes, `*c`omponent interfaces declaring owned field getter/setter and methods are generated.
3. Users `@EntityDef`ine concrete entity classes that are composed from these components.

### Example
One of the most common example of entity class generation is to define `Unit` classes in `Mindustry`. This can be done with `EntityAnno` as such:
```java
// mymod/entities/comp/MyComp.java
package mymod.entities.comp;

import arc.util.*;
import ent.anno.Annotations.*;
import mindustry.gen.*;

// This will generate `Myc` component interface in `mymod.gen.entities`.
@EntityComponent
abstract class MyComp implements Unitc{
    // Do note that `@Override` does *not* mean "replace"
    @Override
    public void update(){
        Log.info("Hello, world!");
    }
}
```
```java
// mymod/entities/comp/EntityDefinitions.java
package mymod.entities.comp;

import ent.anno.Annotations.*;
import mindustry.gen.*;
import mymod.gen.entities.*;

class EntityDefinitions{
    // This will generate `MyUnit` entity class that implements both `MyComp` and `Unit` in `mymod.gen.entities`.
    // The naming in the case of external `@EntityDef`s like in this case is reversed order of the components, without the `*c`:
    // `My` + `Unit` = `MyUnit`.
    // The `Object myUnit` doesn't really matter; it's just there to hold the annotation so the processor recognizes it.
    @EntityDef({Unitc.class, Myc.class}) Object myUnit;
}
```
```java
// mymod/MyMod.java
package mymod;

import mindustry.mod.*;
import mindustry.type.*;
import mymod.gen.entities.*;

public class MyMod extends Mod{
    public static UnitType myUnitType;

    @Override
    public void loadContent(){
        // This will register entity class IDs as well as some mappings. Always call this before anything else!
        EntityRegistry.register();

        myUnitType = EntityRegistry.content("my-unit-type", MyUnit.class, name -> new UnitType(name){{
            // By using `EntityRegistry.content(...)`, the entity mapping is set automatically.
            // This means you don't have to manually set `constructor = MyUnit::create`.
            // If you try to spawn this unit type, it should summon a spriteless flying unit that logs "Hello, world!" constantly.
        }});
    }
}
```

An uncommon example would be generating a non-unit entity that stands out on its own component, without possibly any other special components, such as `EffectState`s. This can be done with `EntityAnno` as such:
```java
// mymod/entities/comp/MyEntityComp.java
package mymod.entities.comp;

import arc.*;
import arc.graphics.g2d.*;
import ent.anno.Annotations.*;
import mindustry.gen.*;
import mymod.gen.entities.*;

// This will generate `MyEntityc` component interface and `MyEntity` entity class in `mymod.gen.entities`.
@EntityComponent(base = true) // `base = true` is to mark the component as base. There may only be 1 or 0 base components (such as `Unit`, `Building`, etc.) in entity definitions.
@EntityDef(MyEntityc.class) // As you can see, unlike in the previous example, the `@EntityDef` is declared internally here. The resulting entity class name will be `MyEntity`, with no regards to the reverse order naming convention.
abstract class MyEntityComp implements Drawc, Rotc{
    // Usage of `@Import` is a *must* to declare fields that come from another components.
    // In this case, `x` and `y` is from `PosComp` (that is implicitly implemented by `DrawComp`), and `rotation` is from `RotComp`.
    @Import float x, y, rotation;

    @Override
    public void draw(){
        Draw.rect(Core.atlas.find("error"), x, y, rotation);
    }
}
```
Spawning a `MyEntity` in the world should summon an entity that only draws an "oh no" sprite.

For more features offered by `EntityAnno`, please read the documentations written in `[the `Annotations` file]`(/entity/src/ent/anno/Annotations.java).
