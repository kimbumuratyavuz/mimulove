const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: "demo-no-project",
    firestore: {
      host: "127.0.0.1",
      port: 8085,
      rules: fs.readFileSync("firestore.rules", "utf8"),
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("unauthenticated user cannot read or write users", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("users").doc("userA").get());
  await assertFails(unauthedDb.collection("users").doc("userA").set({ displayName: "A" }));
});

test("authenticated user can create own profile and pair code", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertSucceeds(
    userADb.collection("users").doc("userA").set({
      userId: "userA",
      displayName: "Alice",
      pairCode: "A1B2C3",
    })
  );
  await assertSucceeds(
    userADb.collection("pair_codes").doc("A1B2C3").set({
      code: "A1B2C3",
      userId: "userA",
      userName: "Alice",
    })
  );
});

test("user cannot overwrite another user's pair code", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  await assertSucceeds(
    userADb.collection("pair_codes").doc("A1B2C3").set({
      code: "A1B2C3",
      userId: "userA",
      userName: "Alice",
    })
  );

  const userBDb = testEnv.authenticatedContext("userB").firestore();
  await assertFails(
    userBDb.collection("pair_codes").doc("A1B2C3").set({
      code: "A1B2C3",
      userId: "userB",
      userName: "Bob",
    })
  );
});

test("couple members can read and update shared couple", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  const userBDb = testEnv.authenticatedContext("userB").firestore();
  const userCDb = testEnv.authenticatedContext("userC").firestore();

  // Alice creates couple with Bob
  await assertSucceeds(
    userADb.collection("couples").doc("couple1").set({
      coupleId: "couple1",
      user1Id: "userA",
      user2Id: "userB",
      user1Name: "Alice",
      user2Name: "Bob",
      isMatched: true,
      anniversaryDate: Date.now(),
    })
  );

  // Bob can read and update
  await assertSucceeds(userBDb.collection("couples").doc("couple1").get());
  await assertSucceeds(
    userBDb.collection("couples").doc("couple1").update({
      lastHeartSenderId: "userB",
      lastHeartCount: 1,
    })
  );

  // Charlie (stranger) cannot read or update
  await assertFails(userCDb.collection("couples").doc("couple1").get());
  await assertFails(
    userCDb.collection("couples").doc("couple1").update({
      lastHeartSenderId: "userC",
    })
  );
});

test("couple member can unlink couple and clear partner coupleId", async () => {
  const userADb = testEnv.authenticatedContext("userA").firestore();
  const userBDb = testEnv.authenticatedContext("userB").firestore();

  // Seed userB
  await assertSucceeds(
    userBDb.collection("users").doc("userB").set({
      userId: "userB",
      displayName: "Bob",
      coupleId: "couple1",
    })
  );

  // Seed couple
  await assertSucceeds(
    userADb.collection("couples").doc("couple1").set({
      coupleId: "couple1",
      user1Id: "userA",
      user2Id: "userB",
      isMatched: true,
    })
  );

  // User A sets isMatched = false
  await assertSucceeds(
    userADb.collection("couples").doc("couple1").update({
      isMatched: false,
    })
  );

  // User A clears User B's coupleId
  await assertSucceeds(
    userADb.collection("users").doc("userB").update({
      coupleId: "",
    })
  );
});
