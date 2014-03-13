# snowball
Snowball, inspired by [Snowflake](https://github.com/twitter/snowflake), is a Java library that generates unique ID numbers at high scale.

# Why Snowball over Snowflake?
Snowball is primarily a port of Snowflake's IdWorker Scala code to Java.  As such, Snowball satisfies the same requirements laid out by Snowflake:

* Performance: Minimum 10,000 ids per second per process
* Uncoordinated: Nodes generating ids are not coordinating with other nodes.
* Roughly Time Ordered: Ids generated in a single thread are strictly time ordered.  Ids generated across multiple threads are not strictly time ordered.
* Directly Sortable:  Ids generated are strictly non-decreasing as a function of time for a given thread.
* Compact:  The ids are 64-bits in size.
* Highly Available:  Id generation is as available as the process in which Snowball is running.

Snowball's generated ids are slightly different.  Snowball ids are comprised of the following components:

* highest 41-bits are a timestamp at millisecond precision with custom epoch.  Unique ids can be generated for over 69 years.
* next bits are a configured node identifier.  This identifier must be unique amongst all distributed id generators.
* next bits are a thread identifier.  This identifier is auto incrementing with a rollover which limits the number of concurrent threads that can be generating ids.
* last bits are a thread sequence number.  This number is auto incrementing with a rollover which safeguards to prohibit rollover in the same thread, in the same millisecond.

The main difference is the thread identifier portion of the Snowball ids.  The actual implementation is basically taking Snowflakes's sequence number portion and splitting it in two portions.  The two portions being Snowball's thread identifier and thread sequence number.

With this change, Snowball can generate unique ids at a faster rate than Snowflake, over 100,000 per second per node.  The cost for this change is a smaller number of nodes.  Snowflake supports 1,024 nodes where as Snowball only supports 256 nodes.

# Why Snowflake over Snowball?
Snowflake provides some things that are not available in Snowball:

* Snowflake can use Apache Zookeeper to generate unique node identifiers.  Such identifiers must be manually chosen and configured in Snowball.
* Snowflake has a server component so id generation can be deployed in a centralized fashion but be available to a larger number of processes that need unique ids.  Snowball has no server component.

# How to Use Snowball

Using Snowball is quite simple.  First, create an `IdGenerator` instance using a unique node identifier.  Then, call the `nextId` method as many times as needed to generated unique ids.  It is as simple as that.

    // generate 100 ids
    long[] ids = new long[100];
    snowball.IdGenerator generator = new IdGenerator(someUniqueNodeId);
    for (int i = 0; i < ids.length; ++i) {
        ids[i] = generator.nextId();
    }



